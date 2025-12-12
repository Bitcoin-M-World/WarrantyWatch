package com.tux2gaming.warrantychecker;

import java.io.BufferedReader;
import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

public class CanaanWarrantyStuff {
	
	/*String SN = "";
	String MODEL = "";
	String HTH = "";
	String JZRQ = "";
	String BXNAME = "";
	String BXTS = "";*/
	String warranty = "";

	public CanaanWarrantyStuff() {
	}
	
	public CanaanWarrantyStuff(BufferedReader in) {
		JsonReader reader = new JsonReader(in);
		try {
			while(reader.hasNext()) {
				if(reader.peek() == JsonToken.BEGIN_OBJECT) {
					reader.beginObject();
				}
				String name = reader.nextName();
				switch(name) {
					case "msg":
						if(reader.peek() == JsonToken.BEGIN_OBJECT) {
							reader.beginObject();
						}else {
							reader.close();
							return;//Improper S/N don't need to process any more
						}
						break;
					case "response":
						reader.beginArray();
						reader.beginObject();
						break;
					case "Expired":
						warranty = reader.nextString();
						reader.close();
						return;
					default:
						reader.skipValue();
				}
						
			}
		} catch (IOException e) {
		}finally {
			try {
				reader.close();
			} catch (IOException e) {
			}
		}
	}
	
	public String getWarranty() {
		return warranty;
	}
	
	public void setWarranty(String warranty) {
		this.warranty = warranty;
	}

}
