package Main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import net.sf.json.JSONArray;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class APIService implements HttpHandler {
	@Override
	public void handle(HttpExchange t) {
		Runnable r = new APIThread(t);
		new Thread(r).start();
	}
}

class APIThread implements Runnable {
	//TODO: PLACE BASE URI HERE
	private final String _baseURI = "/facePay";
	private HttpExchange t;
	private String requestString;
	private JsonObject jsonRequest;
	private String response;
	private StringBuilder responseBuffer;
	private int httpResponseCode;
	
	public APIThread(HttpExchange parameter) {
		t = parameter;
	}

	public void run() {
		System.out.println("Client");
		InputStream is; // used for reading in the request data
		OutputStream os; // used for writing out the response data
		requestString = "";
		jsonRequest = new JsonObject();
		response = "";

		// put the response text in this buffer to be sent out at the end
		responseBuffer = new StringBuilder(); 
		// This is where the HTTP response code to send back to the client should go
		httpResponseCode = 404;

		String uri = t.getRequestURI().getPath();
		String requestMethod = t.getRequestMethod();
		String requestQuery = t.getRequestURI().getQuery();

		// We parse the GET parameters through a Filter object that is registered in ServerBootstrap
		// It is possible to parse POST parameters like this too, but I don't want to
		Map<String, String> getParams = new HashMap<String, String>();
		
		
		// GET Requests won't have any data in the body
		if(requestMethod.equalsIgnoreCase("GET")){
			getParams = ServerUtils.parseQuery(requestQuery);
			System.out.println(new Date().toString() + ": ");
		} 		
		else if (requestMethod.equalsIgnoreCase("POST")) {
			try {
				StringBuilder requestBuffer = new StringBuilder();
				is = t.getRequestBody();
				int rByte;
				while ((rByte = is.read()) != -1) {
					requestBuffer.append((char) rByte);
				}
				is.close();

				if (requestBuffer.length() > 0) {
					requestString = URLDecoder.decode(requestBuffer.toString(), "UTF-8");
				} else {
					requestString = null;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/*
		 * code to respond to each type of request goes here THIS IS WHERE THE MAGIC HAPPENS
		 * 
		 * requestString is a String that hold JSON (or not) data to be parsed
		 * and acted upon uri is a String that holds the request path
		 * (disregards http://hostname:port as well as any GET parameters)
		 * responseBuffer is a StringBuilder that you write your return data to
		 * in JSON format httpResponseCode is an int that holds what response
		 * code you want to return
		 */
		System.out.println(new Date().toString() + ": ");
		try {
			//TODO: DETERMINE IF POST OR GET... THEN CALL METHOD BASED ON PATH.
			if (requestMethod.equalsIgnoreCase("POST")) {
				if (uri.equals(_baseURI+"/login") || uri.equals(_baseURI+"/login/")) {
					loginUser();
				}else if (uri.equals(_baseURI+"/newUser") || uri.equals(_baseURI+"/newUser/")) {
					registerUser();
				}
				else if (uri.equals(_baseURI+"/enroll") || uri.equals(_baseURI+"/enroll/")) {
					//send image to Kairos
					enroll();
				}
				else if (uri.equals(_baseURI+"/recognize") || uri.equals(_baseURI+"/recognize/")) {
					//try to recognize person in photo using Kairos images
					recognize();
				}
				
			} else if (requestMethod.equalsIgnoreCase("GET")){
				if (uri.equals(_baseURI+"/getUserInfo") || uri.equals(_baseURI+"/getUserInfo/")) {
					getUserInfo(getParams);
				}
				//put get requests here
			}
		} catch (Exception e){
			responseBuffer.append("Failed");
			httpResponseCode = 400;
		}

		// this section sends back the return data
		try {
			response = responseBuffer.toString();

			Headers h = t.getResponseHeaders();
			h.add("Content-Type", "application/json; charset=UTF-8");
			h.add("Access-Control-Allow-Origin", "*");
			h.add("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
			h.add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");

			t.sendResponseHeaders(httpResponseCode, response.getBytes("UTF-8").length);
			os = t.getResponseBody();
			os.write(response.getBytes("UTF-8"), 0, response.getBytes("UTF-8").length);
			os.flush();
			os.close();
			t.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	

	
	public void loginUser(){
		
		//This gets params from post request
		JsonParser parser = new JsonParser();
		jsonRequest = parser.parse(requestString).getAsJsonObject();
		String username = jsonRequest.get("Username").getAsString();
		String pword = jsonRequest.get("UserPassword").getAsString();
		
		boolean valid=false;
		//CHECKS IF VALID AND PUT TOKEN AS A JSON OBJECT INTO RESPONSE BUFFER AND UPDATES RESPONSE CODE.
		if (username != null && pword != null) {
			if(Database.loginUser(username, pword))
			{
				JsonObject jsonResponse = new JsonObject();
				String token = username;
				jsonResponse.addProperty("UserToken", token);
				responseBuffer.append(jsonResponse.toString());
				httpResponseCode = 200;
				valid=true;
			}
			
		}  
			
		if(!valid)
		{
			responseBuffer.append("Invalid UserEmail or UserPassword");
			httpResponseCode = 400;
		}
	}
	
	public void registerUser()
	{
		JsonParser parser = new JsonParser();
		jsonRequest = parser.parse(requestString).getAsJsonObject();
		String email = jsonRequest.get("Username").getAsString();
		String pword = jsonRequest.get("UserPassword").getAsString();

		String prn = createAccountG(email, pword);
		user newUser = new user(email,prn,pword);		

		newUserResponse response = Database.addUser(newUser);
		if(response.success)
		{
			String token = email;
			JsonObject jsonResponse = new JsonObject();			
			jsonResponse.addProperty("UserToken", token);
			responseBuffer.append(jsonResponse.toString());
			httpResponseCode = 200;
			enroll();
		}else
		{
			responseBuffer.append(response.status.toString());
			httpResponseCode = 400;
		}	
	}
	
	private String createAccountG(String email, String pword) {
		System.setProperty("javax.net.ssl.keyStoreType", "pkcs12");
		System.setProperty("javax.net.ssl.keyStore", "res/keystore.p12");
		System.setProperty("javax.net.ssl.keyStorePassword", "letmein");
		SSLSocketFactory sslFact = (SSLSocketFactory) SSLSocketFactory.getDefault();
		 String prn ="";
		
		try{
			Map<String, Object> params = new LinkedHashMap<>();
			params.put("apiLogin", "bJ5GQn-9999");
			params.put("apiTransKey", "lL3CNUjWdn");
			params.put("providerId", "511");
			params.put("transactionId", generateTokenString());
			params.put("prodId", "5094");
			StringBuilder postData = new StringBuilder();
			for (Map.Entry<String,Object> param : params.entrySet()) {
				if (postData.length() != 0) postData.append('&');
				postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
				postData.append('=');
				postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
			}
			byte[] postDataBytes = postData.toString().getBytes("UTF-8");
			URL url = new URL("https://sandbox-api.gpsrv.com/intserv/4.0/createAccount");
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setSSLSocketFactory(sslFact);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
			conn.setDoOutput(true);
			conn.getOutputStream().write(postDataBytes);

			String xmlOutput;
			StringBuilder sb = new StringBuilder();
			
			String pattern = "<pmt_ref_no>(\\d*)<\\/pmt_ref_no>";
			 Pattern r = Pattern.compile(pattern);
			
			Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
			for (int c; (c = in.read()) >= 0;) {
				sb.append((char)c);				
			}
			xmlOutput=sb.toString();
			Matcher m = r.matcher(xmlOutput);
			if(m.find())
			{
				prn=m.group(1);
			}
			
		}catch(MalformedURLException e)
		{
			e.printStackTrace();
			return null;
		}catch(IOException e)
		{
			e.printStackTrace();
			return null;
		}
		return prn;
	}
	
	class enrollInfo {
		String image;
		String subject_id;
		String gallery_name;
	}
	
	class recognizeInfo {
		String image;
		String gallery_name;
	}
	
	public void enroll() {
		Gson gson = new Gson();
		HttpClient client = HttpClientBuilder.create().build();
		HttpPost request = new HttpPost("https://api.kairos.com" + "/enroll");
		request.setHeader("Content-Type", "application.json");
		request.setHeader("app_id", "7d561877");
		request.setHeader("app_key", "f194b9c6004f4f009d3da627836078d9");
		
		JsonParser parser = new JsonParser();
		jsonRequest = parser.parse(requestString).getAsJsonObject();
		String email = jsonRequest.get("Username").getAsString();
		

		//JsonArray arr = jsonRequest.getAsJsonArray("Images");
//		List<String> list = new ArrayList<String>();
//		for(int i = 0; i < arr.size(); i++){
//		    list.add(arr.get(i).getAsString());
//		}		
//		System.out.println("List Size"+list.size());
//		//for(int i=0;i<list.size();i++)
		//{
			enrollInfo info = new enrollInfo();
			info.subject_id = Database.users.get(email).prn;
			info.image = jsonRequest.get("Images").getAsString();
			
			info.image=info.image.replace(' ','+');
			//System.out.println(info.image);
			
			info.gallery_name = "buschemi";
			try {
				StringEntity postingString = new StringEntity(gson.toJson(info));
				request.setEntity(postingString);
				HttpResponse response = client.execute(request);
				
				if(response.getStatusLine().getStatusCode() == 200) {
					System.out.println("Enroll Success!");
					httpResponseCode = 200;
//					JsonParser kparser = new JsonParser();
//	                JsonObject json = kparser.parse(EntityUtils.toString(response.getEntity())).getAsJsonObject();
//	                System.out.println("Printing Json");
//	                System.out.println(json);
				}
				else
				{
					System.out.println("enroll Fail");
					httpResponseCode = 400;
				}	
				
			} catch (IOException e) {
				e.printStackTrace();
			//}
		}	
		
	}
	
	public void recognize() {
		Gson gson = new Gson();
		HttpClient client = HttpClientBuilder.create().build();
		HttpPost request = new HttpPost("https://api.kairos.com" + "/recognize");
		request.setHeader("Content-Type", "application.json");
		request.setHeader("app_id", "7d561877");
		request.setHeader("app_key", "f194b9c6004f4f009d3da627836078d9");
		
		JsonParser parser = new JsonParser();
		jsonRequest = parser.parse(requestString).getAsJsonObject();
		//String email = jsonRequest.get("Username").getAsString();
		
		
		
		recognizeInfo info = new recognizeInfo();		
		info.image = jsonRequest.get("Image").getAsString();		
		info.image=info.image.replace(' ','+');
		
		info.gallery_name = "buschemi";
		try {
			StringEntity postingString = new StringEntity(gson.toJson(info));
			request.setEntity(postingString);
			HttpResponse response = client.execute(request);
			
			if(response.getStatusLine().getStatusCode() == 200) {
				System.out.println("Success!");
				httpResponseCode = 200;
				JsonParser kparser = new JsonParser();
                JsonObject json = kparser.parse(EntityUtils.toString(response.getEntity())).getAsJsonObject();
                
                System.out.println(json.toString());
               String toPrn = json.get("subject_id").getAsString();
               
			}
			else
			{
				
				httpResponseCode = 400;
			}	
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	public String getNameFromPrn(String inPrn)//TODO: This is terrible maybe use prn as the key  later time permitting
	{
		for(user current : Database.users.values())
		{
		//	if(current.prn)
		}
		return null;
	}
	
	
	public void getUserInfo(Map<String, String> params){
		//TODO: GET PARAMS
		String userToken = params.get("UserToken");
		String appToken = params.get("AppToken");
		String[] scopes = params.get("Scopes").split(",");

		if (userToken != null && appToken != null) {
			//TODO MAKE JSON FOR GET REQUEST
			JsonObject jsonResponse = new JsonObject();
			jsonResponse.addProperty("Name", "paywithyoFACE");
			responseBuffer.append(jsonResponse.toString());
			httpResponseCode = 200;
		} else {
			responseBuffer.append("Invalid UserToken");
			httpResponseCode = 400;
		}
		
	}
		
	
	//THIS JUST GENERATES A RANDOM TOKEN... WE DONT NEED TO USE IT.
	public String generateTokenString() {
		Random rng = new Random();
		int length = 49;
		String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
	    String text = "";
	    for (int i = 0; i < length; i++)
	    {
	        text += characters.charAt(rng.nextInt(characters.length()));
	    }
	    return text;
	}
	
}

