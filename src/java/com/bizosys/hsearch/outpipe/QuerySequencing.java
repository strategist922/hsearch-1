package com.bizosys.hsearch.outpipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//import org.apache.hadoop.hsearch.query.QueryContext;
import org.apache.oneline.ApplicationFault;
import org.apache.oneline.SystemFault;
import org.apache.oneline.conf.Configuration;
import org.apache.oneline.pipes.PipeOut;

import com.bizosys.hsearch.query.HQuery;
import com.bizosys.hsearch.query.QueryPlanner;
import com.bizosys.hsearch.query.QueryTerm;

/**
 * There are 2 elements
 * 1 - The Must terms / Optional terms
 * 2 - The term preciousness 
 * -------------------------
 * Must 		Optional
 * -------------------------
 * 0 			0				Do nothing
 * 0			1				Convert optional as must, perform search
 * 0			Many			Load all the IDs (Parallel)
 * 1			0				Regular Search				
 * 1			1				Perform Must, Filter Optional
 * 1			Many			Perform Must, Filtered Optionals in parallel
 * Many			0				Sequential must terms on preciousness order 
 * Many			1				Sequential must terms on preciousness order + Filtered Optional
 * Many			Many			Sequential must terms on preciousness order + Filtered Optionals in parallel 
 */
public class QuerySequencing implements PipeOut{
	
	public QuerySequencing() {
	}	

	public boolean visit(Object objQuery) throws ApplicationFault, SystemFault {
		HQuery query = (HQuery) objQuery;
		//QueryContext ctx = query.ctx;
		QueryPlanner planner = query.planner;
		int mustTermsT = (null == planner.mustTerms) ? 0 : planner.mustTerms.size();
		int optTermsT = (null == planner.optionalTerms) ? 0 : planner.optionalTerms.size();
		
		if ( 0 == mustTermsT) { //Process in 1 step
			if ( 0 == optTermsT) return false;
			
			planner.sequences = new ArrayList<List<QueryTerm>>(1);
			List<QueryTerm> step0 = new ArrayList<QueryTerm>(1);
			step0.addAll(planner.optionalTerms);
			planner.sequences.add(step0);
			
		} else if ( 1 == mustTermsT) {  //Process in 2 steps
			
			planner.sequences = new ArrayList<List<QueryTerm>>(1);
			List<QueryTerm> step0 = new ArrayList<QueryTerm>(1);
			step0.addAll(planner.mustTerms);
			planner.sequences.add(step0);
			
			if ( 0 != optTermsT) {
				List<QueryTerm> step1 = new ArrayList<QueryTerm>(1);
				step1.addAll(planner.optionalTerms);
				planner.sequences.add(step1);
			}
			
		} else { //Find the precious ones and go in sequences

			planner.sequences = new ArrayList<List<QueryTerm>>(1);
			QueryTerm[] qtL = (QueryTerm[])
				planner.mustTerms.toArray(new QueryTerm[mustTermsT]);
			
			Arrays.sort(qtL, new QueryTerm());
			for (QueryTerm term : qtL) {
				List<QueryTerm> step = new ArrayList<QueryTerm>(1);
				step.add(term);
				planner.sequences.add(step);
			}
			
			if ( 0 != optTermsT) {
				List<QueryTerm> optStep = new ArrayList<QueryTerm>(1);
				optStep.addAll(planner.optionalTerms);
				planner.sequences.add(optStep);
			}
		}
		return true;
	}
	
	public boolean commit() throws ApplicationFault, SystemFault {
		return true;
	}

	public PipeOut getInstance() {
		return this;
	}

	public boolean init(Configuration conf) throws ApplicationFault, SystemFault {
		return true;
	}
}
