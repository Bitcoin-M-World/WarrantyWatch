package com.tux2gaming.warrantychecker.threads;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.SwingWorker;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.threeten.extra.YearWeek;

import com.google.gson.Gson;
import com.tux2gaming.warrantychecker.CanaanWarrantyStuff;
import com.tux2gaming.warrantychecker.MinerType;
import com.tux2gaming.warrantychecker.WarrantyDetails;
import com.tux2gaming.warrantychecker.WarrantyStuff;
import com.tux2gaming.warrantychecker.WhatsMinerWarrantyStuff;

public class ParseSerials extends SwingWorker<String, String> {

	private String bitMainApiEndpoint = "https://shop-repair.bitmain.com/api/warranty/getWarranty?serialNumber=";

	private String whatsMinerApiEndpoint = "https://www.whatsminer.com/renren-fast/app/RepairWorkOrder/warranty?str=";
	private String whatsMinerApiVars = "&lang=en_US";

	//private String canaanApiEndpoint = "https://support.canaan.io/Handler/xpc_Handler.ashx?Action=GetZBCX";
	private String canaanApiEndpoint = "https://www.canaan.io/?do_action=action.supports_v2_sn_product_info";

	private String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36";
	Pattern datepattern = Pattern.compile("\\d{2,4}[\\/-]\\d{1,2}[\\/-]\\d{1,2}");
	Pattern serialpattern = Pattern.compile("[A-Z0-9]{17,33}");
	Pattern alphaserialpattern = Pattern.compile("[A-Z0-9]{6}[A-Z]{2,5}([2-3][0-9])([0-5][0-9])[0-9]{7,11}");

	String inputFile = "";
	String outputFile = "";

	MinerType miners = MinerType.BITMAIN;

	private int warrantyCell = -1;

	//Improper input: {"warranty":0,"haveWhiteList":"N","code":0}
	//Proper input: {"warranty":98,"warrantyEndDate":"2023-07-01 00:00:00","haveWhiteList":"N","code":0}

	Gson gson = new Gson();

	boolean go = true;

	public ParseSerials(String inputFile, String outputFile, MinerType miner) {
		this.inputFile = inputFile;
		this.outputFile = outputFile;
		miners = miner;
	}

	@Override
	protected synchronized String doInBackground() throws Exception {

		FileInputStream input = new FileInputStream(inputFile);
		Workbook workbook = new XSSFWorkbook(input);
		Workbook outputWorkbook = null;
		Sheet inputSheet = workbook.getSheetAt(0);
		Sheet outputSheet = null;
		File fOutputFile = new File(outputFile);
		boolean resume = false;
		if(fOutputFile.exists()) {
			FileInputStream outputInput = new FileInputStream(outputFile);
			outputWorkbook = new XSSFWorkbook(outputInput);
			outputSheet = outputWorkbook.getSheet("Warranties");
			if(outputSheet == null) {
				outputSheet = outputWorkbook.createSheet("Warranties");
			}else {
				resume = true;
			}
		}else {
			outputWorkbook = new XSSFWorkbook();
			outputSheet = outputWorkbook.createSheet("Warranties");
		}
		//Row header = outputSheet.createRow(0);

		/*CellStyle headerStyle = outputWorkbook.createCellStyle();

		XSSFFont font = ((XSSFWorkbook) outputWorkbook).createFont();
		font.setFontName("Arial");
		font.setFontHeightInPoints((short) 12);
		font.setBold(true);
		headerStyle.setFont(font);

		Cell headerCell = header.createCell(0);
		headerCell.setCellValue("Serial #");
		headerCell.setCellStyle(headerStyle);

		headerCell = header.createCell(1);
		headerCell.setCellValue("Warranty Expires");
		headerCell.setCellStyle(headerStyle);*/

		int serialColumn = -1;
		//int allRowCurrent = 0;
		int lastRowNum = inputSheet.getLastRowNum() + 1;
		firePropertyChange("total", Integer.valueOf(0), Integer.valueOf(lastRowNum));
		for(int allRowCurrent = 0; allRowCurrent < lastRowNum; allRowCurrent++) {
			Row row = inputSheet.getRow(allRowCurrent);
			if(!go) {
				break;
			}
			try {
				firePropertyChange("varat", Integer.valueOf(allRowCurrent), Integer.valueOf(allRowCurrent+1));
				firePropertyChange("status", "", "Parsing line " + (allRowCurrent + 1) + "/" + lastRowNum);
				if(serialColumn < 0) {
					for(int i = 0; i < row.getLastCellNum(); i++) {
						Cell cell = row.getCell(i);
						if(cell.getCellType() == CellType.STRING) {
							String sCell = cell.getStringCellValue();
							if(serialpattern.matcher(sCell).matches()) {
								System.out.println("Found serial # in column " + i);
								serialColumn = i;
								if(resume) {
									int lastrow = outputSheet.getLastRowNum();
									Row inputrow = inputSheet.getRow(lastrow);
									Row outputrow = outputSheet.getRow(lastrow);
									String inputSerial = "";
									String outputSerial = "";
									if(inputrow.getCell(serialColumn).getCellType() == CellType.STRING) {
										inputSerial = inputrow.getCell(serialColumn).getStringCellValue();
									}
									if(outputrow.getCell(serialColumn).getCellType() == CellType.STRING) {
										outputSerial = outputrow.getCell(serialColumn).getStringCellValue();
									}
									if(inputSerial.equals(outputSerial)) {
										allRowCurrent = lastrow;
										warrantyCell = outputrow.getLastCellNum() -1;
									}else {
										resume = false;
										outputWorkbook = new XSSFWorkbook();
										outputSheet = outputWorkbook.createSheet("Warranties");
									}
								}
								if(!resume) {
									WarrantyDetails warrantyEnd = getWarrantyEndDate(sCell);
									writeRow(outputWorkbook, sCell, warrantyEnd, outputSheet, row, allRowCurrent);
								}
								break;
							}else if(sCell.toLowerCase().indexOf("serial") > -1) {
								System.out.println("Found serial # in column " + i);
								serialColumn = i;
								if(resume) {
									int lastrow = outputSheet.getLastRowNum();
									Row inputrow = inputSheet.getRow(lastrow);
									Row outputrow = outputSheet.getRow(lastrow);
									String inputSerial = "";
									String outputSerial = "";
									if(inputrow.getCell(serialColumn).getCellType() == CellType.STRING) {
										inputSerial = inputrow.getCell(serialColumn).getStringCellValue();
									}
									if(outputrow.getCell(serialColumn).getCellType() == CellType.STRING) {
										outputSerial = outputrow.getCell(serialColumn).getStringCellValue();
									}
									if(inputSerial.equals(outputSerial)) {
										allRowCurrent = lastrow;
										warrantyCell = outputrow.getLastCellNum() -1;
									}else {
										resume = false;
										outputWorkbook = new XSSFWorkbook();
										outputSheet = outputWorkbook.createSheet("Warranties");
									}
								}
								if(!resume) {
									createHeader(outputWorkbook, outputSheet, row, allRowCurrent);
								}
								break;
							}
						}
					}
				}else {
					Cell cell = row.getCell(serialColumn);
					if(cell.getCellType() == CellType.STRING) {
						System.out.println("Writing row " + allRowCurrent);
						String sCell = cell.getStringCellValue();
						if(sCell != null && sCell.trim().length() > 0) {
							WarrantyDetails warrantyEnd = getWarrantyEndDate(sCell);
							writeRow(outputWorkbook, sCell, warrantyEnd, outputSheet, row, allRowCurrent);
						}
					}
				}
			}catch(Exception e) {
				System.out.println("Unable to get warranty info for row " + (allRowCurrent+1));
				System.out.println(e);
			}
			writeWorkbook(outputWorkbook);
			//allRowCurrent++;
		}
		writeWorkbook(outputWorkbook);
		outputWorkbook.close();
		workbook.close();
		if(!go) {
			firePropertyChange("status", "", "Cancelled getting warranties. ");
		}else {
			firePropertyChange("status", "", "Finished getting warranties. ");
		}
		firePropertyChange("done", "", "Done!");
		return null;
	}

	private void writeWorkbook(Workbook outputWorkbook) {
		try {
			FileOutputStream outputStream = new FileOutputStream(outputFile);
			outputWorkbook.write(outputStream);
			outputStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private synchronized WarrantyDetails getWarrantyEndDate(String serial) {
		return getWarrantyEndDate(serial, miners);
	}

	private synchronized WarrantyDetails getWarrantyEndDate(String serial, MinerType type) {
		if(type == MinerType.BITMAIN) {
			try {
				if(miners != MinerType.AUTO) {//We are the last in the list, so no waiting!
					try {
						wait(4*1000);
					}catch(InterruptedException ex) {

					}
				}
				boolean grabbed = false;
				do {
					URL url = getBitmainUrl(serial);
					HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
					con.setRequestMethod("GET");
					con.setRequestProperty("User-Agent", userAgent);
					con.setRequestProperty("accept", "application/json, text/plain, */*");
					con.setRequestProperty("accept-language", "en-US,en;q=0.7");
					con.setRequestProperty("origin", "https://m.bitmain.com");
					con.setRequestProperty("referer", "https://m.bitmain.com/");
					con.setRequestProperty("sec-ch-ua", "\"Brave\";v=\"111\", \"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"111\"");
					con.setRequestProperty("sec-ch-ua-mobile", "?0");
					con.setRequestProperty("sec-ch-ua-platform", "\"Linux\"");
					int responseCode = con.getResponseCode();
					if(responseCode == HttpsURLConnection.HTTP_OK) {
						BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
						WarrantyStuff warr = gson.fromJson(in, WarrantyStuff.class);
						if(warr != null) {
							Matcher match = datepattern.matcher(warr.getWarrantyEndDate());
							if(match.find()) {
								warr.setWarrantyEndDate(match.group());
							}
							grabbed = true;
							return new WarrantyDetails(MinerType.BITMAIN, warr.getWarrantyEndDate(), serial);
						}else {
							return null;
						}
					}else {
						int waitPeriod = 60*1000;
						if(responseCode == 429) {
							String retry = con.getHeaderField("Retry-After");
							if(retry != null) {
								System.out.println("We got rate limited! Retry period: " + retry);
								try {
									waitPeriod = Integer.parseInt(retry)*1000;
								}catch(NumberFormatException exc) {

								}
							}
						}
						System.out.println("We got rate limited! Waiting... Response code: " + responseCode);
						try {
							wait(waitPeriod);
						}catch(InterruptedException ex) {

						}
					}
				}while(!grabbed);
			} catch (Throwable e) {
				e.printStackTrace();
			}
			return null;
		}else if(type == MinerType.ALPHAMINER) {
			try {
				Matcher serialmatcher = alphaserialpattern.matcher(serial);
				if(serialmatcher.matches()) {
					int year = Integer.parseInt(serialmatcher.group(1)) + 2000;
					int week = Integer.parseInt(serialmatcher.group(2));
					if(week > 53) {
						return null;
					}
					YearWeek yw = YearWeek.of(year, week);
					LocalDate date = yw.atDay(DayOfWeek.FRIDAY);
					date = date.plus(6, ChronoUnit.MONTHS);
					return new WarrantyDetails(MinerType.ALPHAMINER, date.toString(), serial);
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
			return null;
		}else if(type == MinerType.WHATSMINER) {
			try {
				if(miners == MinerType.WHATSMINER) {//Only sleep if we aren't doing multiple queries
					try {
						wait(500);
					}catch(InterruptedException ex) {

					}
				}
				boolean grabbed = false;
				do {
					URL url = getWhatsMinerUrl(serial);
					HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
					con.setRequestMethod("GET");
					con.setRequestProperty("User-Agent", userAgent);
					con.setRequestProperty("accept", "application/json, text/plain, */*");
					con.setRequestProperty("accept-language", "en-US,en;q=0.7");
					con.setRequestProperty("referer", "https://www.whatsminer.com/src/views/support.html");
					con.setRequestProperty("sec-ch-ua", "\"Brave\";v=\"111\", \"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"111\"");
					con.setRequestProperty("sec-ch-ua-mobile", "?0");
					con.setRequestProperty("sec-ch-ua-platform", "\"Linux\"");
					int responseCode = con.getResponseCode();
					if(responseCode == HttpsURLConnection.HTTP_OK) {
						BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
						WhatsMinerWarrantyStuff warr = gson.fromJson(in, WhatsMinerWarrantyStuff.class);
						if(warr != null && warr.getDateList().length > 1) {
							Matcher match = datepattern.matcher(warr.getDateList()[1]);
							if(match.find()) {
								warr.setWarranty(match.group());
							}
							grabbed = true;
							return new WarrantyDetails(type, warr.getWarranty(), serial);
						}else {
							grabbed = true;
							return null;
						}
					}else {
						int waitPeriod = 60*1000;
						if(responseCode == 429) {
							String retry = con.getHeaderField("Retry-After");
							if(retry != null) {
								System.out.println("We got rate limited! Retry period: " + retry);
								try {
									waitPeriod = Integer.parseInt(retry)*1000;
								}catch(NumberFormatException exc) {

								}
							}
						}
						System.out.println("We got rate limited! Waiting... Response code: " + responseCode);
						try {
							wait(waitPeriod);
						}catch(InterruptedException ex) {

						}
					}
				}while(!grabbed);
			} catch (Throwable e) {
				e.printStackTrace();
			}
			return null;
		}else if(type == MinerType.CANAAN) {
			try {
				try {
					wait(500);
				}catch(InterruptedException ex) {

				}
				boolean grabbed = false;
				do {
					URL url = getCanaanUrl();
					HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
					con.setDoOutput(true);
					con.setRequestMethod("POST");
					con.setRequestProperty("User-Agent", userAgent);
					con.setRequestProperty("accept", "application/json, text/plain, */*");
					con.setRequestProperty("accept-language", "en-US,en;q=0.7");
					//con.setRequestProperty("referer", "https://support.canaan.io/en/warrantycheck.aspx");
					con.setRequestProperty("referer", "https://www.canaan.io/support/warranty_check");
					//con.setRequestProperty("sec-ch-ua", "\"Brave\";v=\"111\", \"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"111\"");
					con.setRequestProperty("sec-ch-ua", "\"Chromium\";v=\"142\", \"Brave\";v=\"142\", \"Not_A Brand\";v=\"99\"");
					con.setRequestProperty("sec-ch-ua-mobile", "?0");
					con.setRequestProperty("sec-ch-ua-platform", "\"Linux\"");
					con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
					String serialSearch = "&list%5B0%5D%5BSN%5D=" + serial + "&list%5B0%5D%5BSymptom%5D=&list%5B0%5D%5BRemark%5D=";
					//String serialSearch = "&search=[{\"sn\":\"" + serial + "\",\"ht\":\"\"}]";
					byte[] out = serialSearch.getBytes(StandardCharsets.UTF_8);
					int length = out.length;
					con.setFixedLengthStreamingMode(length);
					con.connect();
					try(OutputStream os = con.getOutputStream()) {
						os.write(out);
					}catch(Exception ex) {

					}
					int responseCode = con.getResponseCode();
					if(responseCode == HttpsURLConnection.HTTP_OK) {
						BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
						CanaanWarrantyStuff warr = new CanaanWarrantyStuff(in);
						//CanaanWarrantyStuff[] warr = gson.fromJson(in, CanaanWarrantyStuff[].class);
						if(warr != null  && warr.getWarranty().length() > 1) {
							Matcher match = datepattern.matcher(warr.getWarranty());
							if(match.find()) {
								warr.setWarranty(match.group());
							}
							grabbed = true;
							return new WarrantyDetails(MinerType.CANAAN, warr.getWarranty().replace("/", "-"), serial);
						}else {
							grabbed = true;
							return null;
						}
					}else {
						int waitPeriod = 60*1000;
						if(responseCode == 429) {
							String retry = con.getHeaderField("Retry-After");
							if(retry != null) {
								System.out.println("We got rate limited! Retry period: " + retry);
								try {
									waitPeriod = Integer.parseInt(retry)*1000;
								}catch(NumberFormatException exc) {

								}
							}
						}
						System.out.println("We got rate limited! Waiting... Response code: " + responseCode);
						try {
							wait(waitPeriod);
						}catch(InterruptedException ex) {

						}
					}
				}while(!grabbed);
			} catch (Throwable e) {
				e.printStackTrace();
			}
			return null;
		}else if(type == MinerType.AUTO) {
			WarrantyDetails warranty = null;
			System.out.println("Checking serial against Canaan");
			warranty = getWarrantyEndDate(serial, MinerType.CANAAN);
			if(warranty != null) {
				System.out.println("Found Serial number!");
				Matcher match = datepattern.matcher(warranty.getWarranty());
				if(match.find()) {
					return warranty;
				}
			}
			System.out.println("Checking serial against Whatsminer");
			warranty = getWarrantyEndDate(serial, MinerType.WHATSMINER);
			if(warranty != null) {
				System.out.println("Found Serial number!");
				Matcher match = datepattern.matcher(warranty.getWarranty());
				if(match.find()) {
					return warranty;
				}
			}
			System.out.println("Checking serial against Bitmain");
			warranty = getWarrantyEndDate(serial, MinerType.BITMAIN);
			if(warranty != null) {
				System.out.println("Found Serial number!");
				Matcher match = datepattern.matcher(warranty.getWarranty());
				if(match.find()) {
					return warranty;
				}
			}
			System.out.println("Checking serial against Alpha Miner");
			warranty = getWarrantyEndDate(serial, MinerType.ALPHAMINER);
			if(warranty != null) {
				System.out.println("Found Serial number!");
				Matcher match = datepattern.matcher(warranty.getWarranty());
				if(match.find()) {
					return warranty;
				}
			}
			return null;
		}
		return null;
	}

	private void writeRow(Workbook workbook, String serial, WarrantyDetails expiration, Sheet sheet, Row oldsheet, int irow) {
		CellStyle headerStyle = null;
		Row row = sheet.createRow(irow);
		Cell cell = null;
		if(warrantyCell < 0) {
			warrantyCell = oldsheet.getLastCellNum();
		}
		for(int i = 0; i < oldsheet.getLastCellNum(); i++) {
			cell = row.createCell(i);
			CellType cellType = oldsheet.getCell(i).getCellType();
			switch (cellType) {
			case _NONE:
			case BLANK:
			case ERROR:
				break;
			case BOOLEAN:
				cell.setCellValue(oldsheet.getCell(i).getBooleanCellValue());
				break;
			case FORMULA:
				cell.setCellValue(oldsheet.getCell(i).getCellFormula());
				break;
			case NUMERIC:
				cell.setCellValue(oldsheet.getCell(i).getNumericCellValue());
				break;
			case STRING:
				cell.setCellValue(oldsheet.getCell(i).getStringCellValue());
				break;
			default:
				break;
			}
			headerStyle = workbook.createCellStyle();
			headerStyle.cloneStyleFrom(oldsheet.getCell(warrantyCell-1).getCellStyle());
			cell.setCellStyle(headerStyle);
		}
		if(expiration != null) {

			cell = row.createCell(warrantyCell);
			cell.setCellValue(expiration.getWarranty());
			cell.setCellStyle(headerStyle);
			if(miners == MinerType.AUTO) {
				cell = row.createCell(warrantyCell + 1);
				String mtype = "";
				switch (expiration.getType()) {
				case BITMAIN:
					mtype = "Bitmain";
					break;
				case CANAAN:
					mtype = "Canaan";
					break;
				case WHATSMINER:
					mtype = "Whatsminer";
					break;
				case ALPHAMINER:
					mtype = "Alpha Miner";
					break;

				default:
					mtype = "Unknown";
					break;
				}
				cell.setCellValue(mtype);
				cell.setCellStyle(headerStyle);
			}
		}
	}

	private void createHeader(Workbook workbook, Sheet sheet, Row oldsheet, int irow) {
		try {
			CellStyle headerStyle = null;
			Row row = sheet.createRow(irow);
			Cell cell = null;
			if(warrantyCell < 0) {
				warrantyCell = oldsheet.getLastCellNum();
			}
			for(int i = 0; i < oldsheet.getLastCellNum(); i++) {
				cell = row.createCell(i);
				CellType cellType = oldsheet.getCell(i).getCellType();
				switch (cellType) {
				case _NONE:
				case BLANK:
				case ERROR:
					break;
				case BOOLEAN:
					cell.setCellValue(oldsheet.getCell(i).getBooleanCellValue());
					break;
				case FORMULA:
					cell.setCellValue(oldsheet.getCell(i).getCellFormula());
					break;
				case NUMERIC:
					cell.setCellValue(oldsheet.getCell(i).getNumericCellValue());
					break;
				case STRING:
					cell.setCellValue(oldsheet.getCell(i).getStringCellValue());
					break;
				default:
					break;
				}
				headerStyle = workbook.createCellStyle();
				headerStyle.cloneStyleFrom(oldsheet.getCell(warrantyCell-1).getCellStyle());
				cell.setCellStyle(headerStyle);
			}
			cell = row.createCell(warrantyCell);
			cell.setCellValue("Warranty Expires");
			cell.setCellStyle(headerStyle);
			if(miners == MinerType.AUTO) {
				cell = row.createCell(warrantyCell + 1);
				cell.setCellValue("Miner Type");
				cell.setCellStyle(headerStyle);
			}
		}catch(Exception e) {
			System.out.println(e);
		}
	}

	private URL getBitmainUrl(String serial) throws Throwable {
		return new URL(bitMainApiEndpoint + serial);
	}

	private URL getWhatsMinerUrl(String serial) throws Throwable {
		return new URL(whatsMinerApiEndpoint + serial + whatsMinerApiVars);
	}

	private URL getCanaanUrl() throws Throwable {
		return new URL(canaanApiEndpoint);
	}

	public void stopIt() {
		go = false;
	}

}
