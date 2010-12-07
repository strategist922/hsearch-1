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
package com.bizosys.hsearch.inpipe.util;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bizosys.hsearch.inpipe.InpipeLog;
import com.bizosys.oneline.SystemFault;
import com.bizosys.oneline.conf.Configuration;
import com.bizosys.oneline.services.Request;
import com.bizosys.oneline.services.Response;
import com.bizosys.oneline.services.Service;
import com.bizosys.oneline.services.ServiceMetaData;
import com.bizosys.oneline.services.scheduler.ExpressionBuilder;
import com.bizosys.oneline.services.scheduler.ScheduleTask;

/**
 * This service refreshes the stopwords in intervals.
 * @author karan
 *
 */
public class StopwordManager implements Service{
	
	protected Set<String> stopWords = null;
	protected Set<String> EMPTY_WORDS = new HashSet<String>(1);
	ScheduleTask scheduledRefresh = null;
	
	private static StopwordManager instance = null;
	public static StopwordManager getInstance() throws SystemFault {
		if ( null == instance) throw new SystemFault(
			"StopwordManager is not initialized");
		return instance;
	}
	
	public StopwordManager() {
		instance = this;
	}
	
	public String getName() {
		return "StopwordManager";
	}

	/**
	 * Launches dictionry refresh task and initializes the dictionry.
	 */
	public boolean init(Configuration conf, ServiceMetaData arg1) {
		InpipeLog.l.info("StopwordManager > Initializing Stopword Service.");
		StopwordRefresh refreshTask = new StopwordRefresh();

		int refreshInteral = conf.getInt("stopword.refresh", 30);
		ExpressionBuilder expr = new ExpressionBuilder();
		expr.setMinute(refreshInteral, true);
		
		long startTime = new Date().getTime() + 10 * 60 * 1000 /** After 10 minutes */;
		try {
			refreshTask.process();
			scheduledRefresh = new ScheduleTask(refreshTask, expr.getExpression(), 
				new Date(startTime), new Date(Long.MAX_VALUE));
			InpipeLog.l.info("StopwordManager > Stopword Refresh task is scheduled.");
			return true;
		} catch (Exception ex) {
			InpipeLog.l.fatal("StopwordManager Initialization failed >", ex);
			return false;
		}
	}

	public void process(Request arg0, Response arg1) {
	}

	public void stop() {
		if ( null != this.scheduledRefresh) 
			this.scheduledRefresh.endDate = new Date(System.currentTimeMillis());
	}	

	public Set<String> getStopwords() {
		if ( null == stopWords) return EMPTY_WORDS; 
		return stopWords;
	}

	/**
	 * Set a new stopword list. This also refreshes the existing list.
	 * @param words
	 * @throws SystemFault
	 */
	public void setStopwords(List<String> words) throws SystemFault {
		StopwordRefresh.add(words);
		Set<String> newStopWords = new HashSet<String>();
		newStopWords.addAll(words);
		this.stopWords = newStopWords;
	}
	
	/**
	 * This modifies local stopwords only
	 * @param words New Stop Words
	 * @throws SystemFault
	 */
	public void setLocalStopwords(List<String> words) throws SystemFault {
		Set<String> newStopWords = new HashSet<String>();
		newStopWords.addAll(words);
		this.stopWords = newStopWords;
	}
	
}
