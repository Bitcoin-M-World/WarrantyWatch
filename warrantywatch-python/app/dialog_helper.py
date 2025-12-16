import tkinter
import tkinter.filedialog
import sys

def pick_save_path():
    try:
        root = tkinter.Tk()
        root.withdraw() # Hide the main window
        root.wm_attributes('-topmost', 1) # Bring to front
        
        file_path = tkinter.filedialog.asksaveasfilename(
            defaultextension=".xlsx",
            filetypes=[("Excel files", "*.xlsx"), ("All files", "*.*")],
            title="Select Output Location"
        )
        
        if file_path:
            print(file_path)
            return file_path
    except Exception as e:
        pass
    return ""

if __name__ == "__main__":
    pick_save_path()
