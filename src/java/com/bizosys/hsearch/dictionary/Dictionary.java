/*
* Copyright 2010 The Apache Software Foundation
*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.bizosys.hsearch.dictionary;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bizosys.hsearch.common.Storable;
import com.bizosys.hsearch.hbase.HDML;
import com.bizosys.hsearch.hbase.HReader;
import com.bizosys.hsearch.hbase.HWriter;
import com.bizosys.hsearch.hbase.IScanCallBack;
import com.bizosys.hsearch.hbase.NV;
import com.bizosys.hsearch.schema.IOConstants;
import com.bizosys.hsearch.util.RecordScalar;
import com.bizosys.oneline.ApplicationFault;
import com.bizosys.oneline.SystemFault;
import com.bizosys.oneline.util.StringUtils;

/**
 * Dictionary has around 1 Second. This should be taken care by 
 * batching this. BatchProcessor should do this one by one.
 * We should also perform mass updates. 
 * @author karan
 *
 */
public class Dictionary implements IScanCallBack {
	private static final char KEYWORD_SEPARATOR = '\t';
	int termMergeFactor = 1000;
	int pageSize = 1000;

	StringBuilder runningBuf = new StringBuilder(7000);
	int keywordBufferCount = 0;
	List<String> mergedWordLines = new ArrayList<String>(100);
	List<String> tempWordLines = new ArrayList<String>(100);
	
	public Dictionary(int termMergeFactor, int pageSize) {
		this.termMergeFactor = termMergeFactor;
		this.pageSize = pageSize;
	}
	
	/**
	 * Add entries to the dictionary
	 * @param keywords
	 * @throws ApplicationFault
	 */
	public void add(Hashtable<String, DictEntry> keywords) throws ApplicationFault {
		if ( null == keywords) return;
		try {

			List<RecordScalar> records = new ArrayList<RecordScalar>(keywords.size());
			for (DictEntry entry : keywords.values()) {
				if ( null == entry) continue;
				if ( null == entry.fldWord) continue;

				DictEntryMerge scalar = new DictEntryMerge(
					new Storable(entry.fldWord), IOConstants.DICTIONARY_BYTES,
					IOConstants.DICTIONARY_TERM_BYTES, entry);
				records.add(scalar);
			}
			HWriter.mergeScalar(IOConstants.TABLE_DICTIONARY, records);
		} catch (Exception ex) {
			HLog.l.error(ex);
			throw new ApplicationFault(ex);
		}
	}
	
	/**
	 * Find exact entry detail from the dictionry
	 * @param keyword
	 * @return
	 * @throws ApplicationFault
	 */
	public DictEntry get(String keyword) throws ApplicationFault {
		if ( StringUtils.isEmpty(keyword) ) return null;
		try {
			NV kv = new NV(IOConstants.DICTIONARY_BYTES, IOConstants.DICTIONARY_TERM_BYTES);
			RecordScalar scalar = new RecordScalar(
				Storable.putString(keyword),kv);
			HReader.getScalar(IOConstants.TABLE_DICTIONARY, scalar);
			if ( null == scalar.kv.data) return null;
			return new DictEntry(scalar.kv.data.toBytes());
		} catch (Exception ex) {
			throw new ApplicationFault("Error in dictionary resolution for :" + keyword, ex);
		}
	}
	
	public List<String> getAll() throws ApplicationFault {
		return getAll(null);
	}

	public List<String> getAll(String fromWord) throws ApplicationFault {
		NV nv = new NV(IOConstants.DICTIONARY_BYTES, IOConstants.DICTIONARY_TERM_BYTES);
		byte[] fromPK = ( null == fromWord) ? null : Storable.putString(fromWord);
		List<byte[]> pks = HReader.getAllKeys(IOConstants.TABLE_DICTIONARY,
			nv, fromPK, this.pageSize);
		if ( null == pks) return null;
		
		/**
		 * Optimize memory by removing from list
		 */
		List<String> keywords = new ArrayList<String>(pks.size());
		for (byte[] pk : pks) {
			keywords.add(Storable.getString(pk));
		}
		pks.clear();
		return keywords;
	}
	
	
	/**
	 * Builds the dictionary terms for regex and fuzzy searches  
	 * @throws Exception
	 */
	public synchronized void buildTerms() throws ApplicationFault, SystemFault {
		HLog.l.info("Dictionary term building START");
		runningBuf.delete(0, runningBuf.capacity());
		this.keywordBufferCount = 0;

		NV kv = new NV(IOConstants.DICTIONARY_BYTES, IOConstants.DICTIONARY_TERM_BYTES);
		HReader.getAllValues(IOConstants.TABLE_DICTIONARY, kv, this);

		if ( runningBuf.length() > 0 ) 
			this.tempWordLines.add(runningBuf.toString());
		
		/**
		 * Swap the temp with merged one
		 * TODO:// This is not memory efficient with growing number of words
		 * Think of finding and deleting from the stack
		 */
		List<String> cleanThis = this.mergedWordLines;
		this.mergedWordLines = this.tempWordLines;
		
		cleanThis.clear();
		cleanThis = null;
		this.tempWordLines = new ArrayList<String>();
		runningBuf.delete(0, runningBuf.capacity());
		keywordBufferCount = 0;
		
		HLog.l.info("Dictionary term building END");
	}
	
	/**
	 * The Reader calls this method.. We avoid memory allocation here
	 */
	public void process(byte[] storedBytes) {
		DictEntry entry = new DictEntry(storedBytes);
		if ( keywordBufferCount < termMergeFactor) {
			runningBuf.append(entry.fldWord).append(KEYWORD_SEPARATOR);
			keywordBufferCount++;
		} else {
			HLog.l.debug("Cache reached merge factor limit.");
			this.tempWordLines.add(runningBuf.toString());
			runningBuf.delete(0, runningBuf.capacity());
			keywordBufferCount = 0;
		}
	}
	
	/**
	 * Uses fuzzy mechanism for searchign.
	 * @param searchWord
	 * @param fuzzyFactor
	 * @return
	 */
	public List<String> fuzzy(String searchWord, int fuzzyFactor) {
		DistanceImpl dis = new DistanceImpl();
		List<String> foundWords = new ArrayList<String>();
		int index1, index2;
		String token = null;
		
		for (String text: mergedWordLines) {
			  index1 = 0;
			  index2 = text.indexOf(KEYWORD_SEPARATOR);
			  token = null;
			  while (index2 >= 0) {
				  token = text.substring(index1, index2);
				  index1 = index2 + 1;
				  index2 = text.indexOf(KEYWORD_SEPARATOR, index1);
				  if ( StringUtils.isEmpty(token) ) continue;

				  if ( dis.getDistance(searchWord, token) <= fuzzyFactor) {
					  foundWords.add(token);
				  }
			  }
		}
		return foundWords;
	}
	
	/**
	 * Uses regular expression to find it.
	 * @param searchWord
	 * @return
	 */
	public synchronized List<String> regex(String pattern) {
		Pattern p = Pattern.compile(pattern);
		List<String> matchedWords = new ArrayList<String>();
		
		int readIndex, foundIndex;
		String token = null;
		Matcher m = null;
		for (String text: mergedWordLines) {
			  readIndex = 0;
			  
			  foundIndex = text.indexOf(KEYWORD_SEPARATOR);
			  if ( foundIndex == -1 && text.length() > 0) {
			      m = p.matcher(text);
				  if ( m.find() ) matchedWords.add(text);
			  }
			  
			  token = null;
			  while (foundIndex >= 0) {
				  token = text.substring(readIndex, foundIndex);
			      m = p.matcher(token);
				  if ( m.find() ) matchedWords.add(token);
				  readIndex = foundIndex + 1;
				  foundIndex = text.indexOf(KEYWORD_SEPARATOR, readIndex);
			  }
		}
		return matchedWords;
	}
	
	public void purge() throws SystemFault {
		try {
			NV kv = new NV(IOConstants.DICTIONARY_BYTES, IOConstants.DICTIONARY_TERM_BYTES);
			HDML.truncate(IOConstants.TABLE_DICTIONARY, kv);
		} catch (Exception ex) {
			throw new SystemFault(ex);
		}
	}

	/**
	 * Delete all the keywords appeared. 
	 * @param keywords
	 * @throws ApplicationFault
	 */
	public void delete(List<String> keywords) throws ApplicationFault {
		if ( null == keywords) return;
		try {
			List<byte[]> deletes = new ArrayList<byte[]>();
			for (String keyword : keywords) {
				if ( StringUtils.isEmpty(keyword)) continue;
				byte[] wordB = Storable.putString(keyword);
				deletes.add(wordB);
				continue;
			}
			HWriter.delete(IOConstants.TABLE_DICTIONARY, deletes);
		} catch (Exception ex) {
			HLog.l.error(ex);
			throw new ApplicationFault(ex);
		}
	}	

	/**
	 * Reduce the sightings
	 * @param keywords
	 * @throws ApplicationFault
	 */
	public void substract(Hashtable<String, DictEntry> keywords)
	throws ApplicationFault {
		
		if ( null == keywords) return;
		
		List<RecordScalar> records = new ArrayList<RecordScalar>(keywords.size());
		for (DictEntry entry : keywords.values()) {
			if ( null == entry) continue;
			if ( null == entry.fldWord) continue;

			DictEntrySubstract scalar = new DictEntrySubstract(
				new Storable(entry.fldWord), IOConstants.DICTIONARY_BYTES,
				IOConstants.DICTIONARY_TERM_BYTES, entry);
			records.add(scalar);
		}
		try {
			HWriter.mergeScalar(IOConstants.TABLE_DICTIONARY, records);
		} 	catch (Exception ex) {
			HLog.l.error(ex);
			throw new ApplicationFault(ex);
		}

	}		
}
