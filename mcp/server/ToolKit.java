package com.mcnz.spring.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ToolKit {
	
	  @McpTool(name = "the_safe_word", description = "A safe word to check against.")
	  public String safeWord() throws Exception {
		  	IO.println("In the safeword tool");
		    return "coconut";
	  }
	  
	  @McpTool(name = "save_the_response", description = "Saves the response to the filesystem.")
	  public String saveTheTweets(@McpToolParam()String json) throws Exception {
		  	IO.println("In the save responses tool");
			var directoryForTweets = Path.of("C://_repos//responses");
		    Files.createDirectories(directoryForTweets);

		    var filename = "tweets-" + Instant.now().toEpochMilli() + ".json";
		    var filePath = directoryForTweets.resolve(filename);
		    Files.writeString(filePath, json);
		    return filePath.toString();
	  }	  
	  
	  

}
