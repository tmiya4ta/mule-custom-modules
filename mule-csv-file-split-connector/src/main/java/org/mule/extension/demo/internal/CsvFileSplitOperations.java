package org.mule.extension.demo.internal;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Connection;
import java.util.ArrayList;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import clojure.lang.IFn;
import clojure.java.api.Clojure;
/**
 * This class is a container for operations, every public method in this class
 * will be taken as an extension operation.
 */
public class CsvFileSplitOperations {

	/**
	 * Example of an operation that uses the configuration and a connection instance
	 * to perform some action.
	 */
	@MediaType(value = ANY, strict = false)
	public String retrieveInfo(@Config CsvFileSplitConfiguration configuration,
			@Connection CsvFileSplitConnection connection) {
		return "Using Configuration [" + configuration.getConfigId() + "] with Connection id [" + connection.getId()
				+ "]";
	}

	/**
	 * Example of a simple operation that receives a string parameter and returns a
	 * new string message that will be set on the payload.
	 */
	@MediaType(value = ANY, strict = false)
	public String sayHi(String person) {
		return buildHelloMessage(person);
	}

@MediaType(value = ANY, strict = false)
  public ArrayList<String> split(String srcFile, String targetDirectory, int lines) {
      ArrayList<String> ary =  new ArrayList<String>();

      IFn require = Clojure.var("clojure.core", "require");
      require.invoke(Clojure.read("csv-file-split.core"));

      IFn splitCsv = Clojure.var("csv-file-split.core", "split-csv");
      splitCsv.invoke(srcFile, targetDirectory, lines);
      
      /* try (RandomAccessFile rafi = new RandomAccessFile(srcFile, "r"); */
      /* 	   FileChannel fci = rafi.getChannel(); */
      /* 	   RandomAccessFile rafo = new RandomAccessFile("xa", "w"); */
      /* 	   FileChannel fco = rafi.getChannel();){ */
      
      /* 	  ByteBuffer buf = ByteBuffer.allocate(1024); */
      /* 	  buf.clear(); */
      /* 	  while(-1 < fci.read(buf)) { */
      /* 	      buf.flip(); */
      /* 	      fco.write(buf); */
      /* 	      buf.clear(); */
      /* 	  } */
      /* } catch(Exception e) { */
      /* } */
      return ary;

  }

	/**
	 * Private Methods are not exposed as operations
	 */
	private String buildHelloMessage(String person) {
		return "Hello " + person + "!!!";
	}
}
