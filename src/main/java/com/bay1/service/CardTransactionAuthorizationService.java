package com.bay1.service;

import static com.bay1.service.TxnAuthUtil.getDollarAmt;
import static com.bay1.service.TxnAuthUtil.toBinary;
import static com.bay1.service.TxnAuthUtil.toHex;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class CardTransactionAuthorizationService {
	
	private static final int ZIP_IDX = 5;
	private static final int NAME_IDX = 4;
	private static final int AMT_IDX = 2;
	private static final int EXP_IDX = 1;
	private static final String TXN_OK = "OK";
	private static final String TXN_DECLINE = "DE";
	private static final String TXN_ERROR = "ER";
	
	public void authTxn(String... args) {
        String fileName = args[0];
        
        try(BufferedReader br = new BufferedReader(new FileReader(fileName))) {
	        String line;
	        String response;
	        
	        while ((line = br.readLine()) != null) {
	            System.out.println("\n\nInput: "+line);
	            Map<String, String> dataElementMap = parseAndGetDataElementMap(line);
	            
	            if(!isValidMessage(dataElementMap)) {
	    			response = getResponse(dataElementMap, TXN_ERROR);
	    		} else {
	    			response = getResponse(dataElementMap, 
	    									isValidTxn(dataElementMap) ? TXN_OK : TXN_DECLINE);
	    		}
	            System.out.println("Output: "+response);
	        }
        } catch(IOException e) {
        	System.out.println("Exception occured while reading file: " + e.getMessage());
        }
        
    }
	
	private String getResponse(Map<String, String> dataElementMap, String responseCode) {
		
		StringBuilder response = new StringBuilder();
		char[] setBitsArr = toBinary(dataElementMap.get("bitmap"));
		setBitsArr[3] = '1'; // set for response code
		String responseBitMap = toHex(new String(setBitsArr));
		
		response.append("0110");
		response.append(responseBitMap);
		response.append(hasKeyAndValueIsNotNullOrEmpty(dataElementMap, "pan") ? dataElementMap.get("pan") : "");
		response.append(hasKeyAndValueIsNotNullOrEmpty(dataElementMap, "exp") ? dataElementMap.get("exp") : "");
		response.append(hasKeyAndValueIsNotNullOrEmpty(dataElementMap, "amt") ? dataElementMap.get("amt") : "");
		response.append(responseCode);
		response.append(hasKeyAndValueIsNotNullOrEmpty(dataElementMap, "lenOfName") ? dataElementMap.get("lenOfName") : "");
		response.append(hasKeyAndValueIsNotNullOrEmpty(dataElementMap, "name") ? dataElementMap.get("name") : "");
		response.append(hasKeyAndValueIsNotNullOrEmpty(dataElementMap, "zip") ? dataElementMap.get("zip") : "");
		
		return response.toString();
	}
	
	private boolean isValidTxn(Map<String, String> dataElementMap) {
		/* When Zip code is provided, a transaction is approved if amount is less than $200
		 * and Expiration Date is greater than the current date */
		if(dataElementMap.containsKey("zip") 
				&& dataElementMap.get("zip") != null 
				&& dataElementMap.get("zip").length() == 5) {
			long amt = getDollarAmt(dataElementMap.get("amt"));
			
			return amt < 200 && isValidExpirationDate(dataElementMap.get("exp"));
		}
		/* When Zip code is not provided, a transaction is approved if amount is less than $100
		 * and Expiration Date is greater than the current date */
		if(!dataElementMap.containsKey("zip")) {
			long amt = getDollarAmt(dataElementMap.get("amt"));
			return amt < 100  && isValidExpirationDate(dataElementMap.get("exp"));
		}
		
		return false;
	}
	
	
	/*			
	 *  1. Fetch the bitmap and get binary to identify set bits
	 *  2. Get substring starting index 6 (i.e. starting index of PAN) till end of the string
	 *  3. Start from the end Binary char arr (fetched after converting Hex to Binary)
	 *  4. For each index check if the value is set, index that we need to look are 6, 5, 3, 2, 1 (why not 4 coz that's only for response) 
	 * 		a. if 6 is set THEN substring(str.length()-5) and set the zip value and update the final string 
	 *         without zip i.e. 1651051051051051001225000001 1000 11MASTER YODA
	 *         
	 * 		b. if 5 (Card Holder Name) is set THEN first get the length of the name starting from the end and then then len of the name
	 *         to find out the end of expiration date and update the final string
	 *         i.e. 1651051051051051001225000001 1000
	 *      c. Amt  = substring(str.length()-10) and update the final string
	 *      d. Exp  = substring(str.length()-4) and update the final string
	 *      e. PAN = final string
	 */
	
	private Map<String, String> parseAndGetDataElementMap(String input) {
		Map<String, String> dataElementMap = new HashMap<>();

		String messageTypeIndicator = input.substring(0, 4);
		String bitmap = input.substring(4, 6); 
		char[] setBitsArr = toBinary(bitmap);
		dataElementMap.put("messageTypeIndicator", messageTypeIndicator);
		dataElementMap.put("bitmap", bitmap);
		
		input  = input.substring(6); // without messageTypeIndicator and bitmap value
		
		//when only pan passed messageTypeIndicator and bitmap
		if(input.length() >= 14 && input.length() <= 19) {
			dataElementMap.put("pan", input);
			return dataElementMap;
		}
		
		if(setBitsArr[ZIP_IDX] == '1') {	// is bit 6 i.e. ZIP is set
			input = extractZipAndSetInMap(input, dataElementMap);
		}
		if(setBitsArr[NAME_IDX] == '1') { // is bit 5 i.e. name is set
			input = extractNameAndSetInMap(input, dataElementMap);
		}
		if(setBitsArr[AMT_IDX] == '1') { // is bit 3 i.e. amt set
			input = extractAmountAndSetInMap(input, dataElementMap);
		}
		if(setBitsArr[EXP_IDX] == '1') { // is bit 2 i.e. exp date is set
			input = extractExpiryAndSetInMap(input, dataElementMap);
		}
		
		if(input.length() >= 14) //set when len is 14 or above (max is 19)
			dataElementMap.put("pan", input); //rest string is the pan num
		
		return dataElementMap;
	}
	
	private String extractExpiryAndSetInMap(String input, Map<String, String> dataElementMap) {
		String exp = input.substring(input.length() - 4);  // length of exp date is 4 chars
		dataElementMap.put("exp", exp);
		return input.substring(0, input.length() - 4);
	}
	
	private String extractAmountAndSetInMap(String input, Map<String, String> dataElementMap) {
		String amt = input.substring(input.length()-10);
		dataElementMap.put("amt", amt);
		return input.substring(0, input.length()-10); 
	}
	
	private String extractNameAndSetInMap(String input, Map<String, String> dataElementMap) {
		int j = input.length()-1;
		while(Character.isAlphabetic(input.charAt(j)) 
				|| Character.isSpaceChar(input.charAt(j))) {
			j--;
		}
		dataElementMap.put("name", input.substring(j+1)); // name starting idx is j+1 as j is not a alphabet
		input = input.substring(0, j+1);
		dataElementMap.put("lenOfName", input.substring(input.length()-2)); // last 2 digits of input str
		
		return  input.substring(0, input.length()-2);
	}
	private String extractZipAndSetInMap(String input, Map<String, String> dataElementMap) {
		String zip = input.substring(input.length()-5);
		dataElementMap.put("zip", zip);
		return input.substring(0, input.length()-5); //returns the updated string i.e. without zip 
	}
	
	private boolean isValidMessage(Map<String, String> dataElementMap) {
		//check for required data elements
		if(!dataElementMap.containsKey("pan") 
				|| !dataElementMap.containsKey("exp")
				|| !dataElementMap.containsKey("amt")) {
			return false;
		}
		return true;
	}
	
	private boolean isValidExpirationDate(String expDateStr) {
		// MMYY
		int expYY = Integer.parseInt(expDateStr.substring(2));
		int expMM = Integer.parseInt(expDateStr.substring(0,2));
		
		LocalDate currentdate = LocalDate.now();
		int currentMonth = currentdate.getMonthValue();
		int currentYear = currentdate.getYear()%100; //get last 2 digits only e.g. for 2021 get 21 only
		
		if(expYY > currentYear) return true;
		if (expYY == currentYear && expMM > currentMonth) return true;
		
		return false;
	}
	
	private boolean hasKeyAndValueIsNotNullOrEmpty(Map<String, String> dataElementMap, String key) {
		return dataElementMap.containsKey(key) 
				&& dataElementMap.get(key) != null
				&& dataElementMap.get(key).length() > 0;
			
	}
}
