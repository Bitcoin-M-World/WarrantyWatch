from fastapi import FastAPI, Request, UploadFile, File, Form, BackgroundTasks
from fastapi.templating import Jinja2Templates
from fastapi.staticfiles import StaticFiles
from fastapi.responses import HTMLResponse, FileResponse
import shutil
import os
import uuid
import subprocess
from pathlib import Path
from app.models import MinerType
from app.excel_handler import process_file_logic

app = FastAPI(title="WarrantyWatch Python")

# Constants
BASE_DIR = Path(__file__).resolve().parent.parent
UPLOAD_DIR = BASE_DIR / "uploads"
TEMPLATE_DIR = BASE_DIR / "app" / "templates"
STATIC_DIR = BASE_DIR / "static"

# Ensure dirs
os.makedirs(UPLOAD_DIR, exist_ok=True)
if not os.path.exists(STATIC_DIR):
    os.makedirs(STATIC_DIR)

templates = Jinja2Templates(directory=str(TEMPLATE_DIR))

# Store active jobs in memory (simple version)
# Job ID -> { status: str, progress: int, total: int, message: str, result_file: str }
jobs = {}

def update_progress(job_id, current, total, message):
    if job_id in jobs:
        jobs[job_id]["progress"] = current
        jobs[job_id]["total"] = total
        jobs[job_id]["message"] = message

def process_file_task(job_id: str, input_path: str, output_path: str, miner_type: MinerType):
    try:
        jobs[job_id]["status"] = "processing"
        
        def callback(c, t, m):
            update_progress(job_id, c, t, m)
            
        process_file_logic(input_path, output_path, miner_type, progress_callback=callback)
        
        jobs[job_id]["status"] = "completed"
        jobs[job_id]["message"] = "Completed successfully!"
        jobs[job_id]["progress"] = 100
        jobs[job_id]["total"] = 100
    except Exception as e:
        jobs[job_id]["status"] = "failed"
        jobs[job_id]["message"] = f"Error: {str(e)}"
        print(f"Job {job_id} failed: {e}")

@app.get("/", response_class=HTMLResponse)
async def home(request: Request):
    return templates.TemplateResponse("index.html", {"request": request})

@app.get("/browse-save")
async def browse_save_path():
    """Opens a native file dialog on the server to pick a save location."""
    try:
        # Run the helper script in a subprocess to avoid tkinter main thread issues in Uvicorn
        script_path = BASE_DIR / "app" / "dialog_helper.py"
        result = subprocess.run(
            ["python", str(script_path)], 
            capture_output=True, 
            text=True,
            timeout=60
        )
        path = result.stdout.strip()
        if path:
            return {"path": path}
        return {"path": ""}
    except Exception as e:
        print(f"Error opening dialog: {e}")
        return {"path": ""}

@app.post("/upload", response_class=HTMLResponse)
async def upload_file(
    request: Request,
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    miner_type: MinerType = Form(...),
    output_filename: str = Form(None)
):
    job_id = str(uuid.uuid4())
    
    # Save input file
    input_filename = f"{job_id}_{file.filename}"
    input_path = UPLOAD_DIR / input_filename
    
    # Create output filename
    # Check if a specific path was chosen via dialog (absolute path)
    if output_filename and os.path.isabs(output_filename):
        output_path = Path(output_filename)
        final_name = output_path.name
        # Ensure dir exists
        os.makedirs(output_path.parent, exist_ok=True)
    elif output_filename and output_filename.strip():
        final_name = output_filename.strip()
        if not final_name.lower().endswith(".xlsx"):
            final_name += ".xlsx"
        output_path = UPLOAD_DIR / final_name
    else:
        final_name = f"processed_{file.filename}"
        output_path = UPLOAD_DIR / final_name
        
    with open(input_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
        
    # Init job
    jobs[job_id] = {
        "status": "pending",
        "progress": 0,
        "total": 100,
        "message": "Initializing...",
        "result_file": str(output_path),
        "filename": final_name
    }
    
    background_tasks.add_task(process_file_task, job_id, str(input_path), str(output_path), miner_type)
    
    return templates.TemplateResponse("results.html", {"request": request, "job_id": job_id})

@app.get("/status/{job_id}")
async def get_status(job_id: str):
    return jobs.get(job_id, {"status": "not_found"})

@app.get("/download/{job_id}")
async def download_result(job_id: str):
    job = jobs.get(job_id)
    if not job or job["status"] != "completed":
        return HTMLResponse("File not ready", status_code=404)
    return FileResponse(path=job["result_file"], filename=job["filename"], media_type='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
