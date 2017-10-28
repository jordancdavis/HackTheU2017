package Main;

import java.util.HashMap;
import java.util.Map;

public class ServerUtils {
	
	public static Map<String, String> parseQuery(String query){
		Map<String, String> map = new HashMap<String, String>();
		
		if(query == null){
			return map;
		}
		
		String[] params = query.split("&");
		
		for(String param : params){  
			String name  = param.split("=")[0];
			String value = param.split("=")[1];
						
			map.put(name, value);
		}
		
		return map;
	}

}
