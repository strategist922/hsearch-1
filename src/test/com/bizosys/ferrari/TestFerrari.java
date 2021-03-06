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
package com.bizosys.ferrari;

import java.util.ArrayList;
import java.util.List;

import com.bizosys.oneline.util.StringUtils;

import junit.framework.Test;

public class TestFerrari {
	
	public static void testAll(Test testCase) throws Exception {
		testAll(testCase, 3000);
	}

	public static void testRandom(Test testCase) throws Exception {
		TestRandomValue test = new TestRandomValue();
		test.run(testCase);
        System.out.println("--------------------");
        System.out.println(test.toString());
	}
	
	public static void testI18N(Test testCase) throws Exception {
		TestI18N test = new TestI18N();
		test.run(testCase);
        System.out.println("--------------------");
        System.out.println(test.toString());
	}

	public static void testResponse(Test testCase) throws Exception {
		TestResponseTime test = new TestResponseTime(3000);
		test.run(testCase);
        System.out.println("--------------------");
        System.out.println(test.toString());
	}

	public static void testAll(Test testCase, int waitTime) throws Exception {
		
        TestRandomValue randomTest = new TestRandomValue(); 
        randomTest.run(testCase);

        TestI18N i18NTest = new TestI18N(); 
        i18NTest.run(testCase);

        TestSpecialCharacter spacialCharTest = new TestSpecialCharacter(); 
        spacialCharTest.run(testCase);
        
        TestMaxMinValue maxMinTest = new TestMaxMinValue(); 
        maxMinTest.run(testCase);
        
        TestResponseTime responseTest = new TestResponseTime(100); 
        responseTest.run(testCase);

        TestMemoryUsage memoryTest = new TestMemoryUsage(1024); 
        memoryTest.run(testCase);

        List<String> threadResL = new ArrayList<String>();
        TestThreadSafety.run(testCase, 10, threadResL);

        System.out.println("--------------------");
        System.out.println(randomTest.toString());
        System.out.println(i18NTest.toString());
        System.out.println(spacialCharTest.toString());
        System.out.println(responseTest.toString());
        System.out.println(maxMinTest.toString());
        System.out.println(memoryTest.toString());

        Thread.sleep(waitTime);
        
        System.out.println("\n" + StringUtils.listToString(threadResL, '\n'));
        System.out.println("--------------------");
	}
}
