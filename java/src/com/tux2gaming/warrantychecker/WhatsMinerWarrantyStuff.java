package com.tux2gaming.warrantychecker;

public class WhatsMinerWarrantyStuff {
	
	private String msg = "";
	private int code = 0;
	private String[] dateList = {};
	private String warranty = null;

	public WhatsMinerWarrantyStuff() {
		// TODO Auto-generated constructor stub
	}

	public String getMsg() {
		return msg;
	}

	public String[] getDateList() {
		return dateList;
	}

	public String getWarranty() {
		return warranty;
	}
	
	public void setWarranty(String warranty) {
		this.warranty = warranty;
	}

	public int getCode() {
		return code;
	}
}
