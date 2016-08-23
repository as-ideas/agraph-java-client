/******************************************************************************
** Copyright (c) 2008-2016 Franz Inc.
** All rights reserved. This program and the accompanying materials
** are made available under the terms of the Eclipse Public License v1.0
** which accompanies this distribution, and is available at
** http://www.eclipse.org/legal/epl-v10.html
******************************************************************************/

package tutorial;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openrdf.model.BNode;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;

import com.franz.agraph.repository.AGCatalog;
import com.franz.agraph.repository.AGRepository;
import com.franz.agraph.repository.AGRepositoryConnection;
import com.franz.agraph.repository.AGServer;
import com.franz.agraph.repository.AGValueFactory;
import com.franz.agraph.repository.AGRepositoryConnection.AttributeDefinition;
import com.franz.agraph.repository.UserAttributesContext;

/**
 * A sample class demonstrating the capabilities of AllegroGraph Triple Attributes.
 * Specifically, we demonstrate how Triple Attributes can be used as a mechanism
 * for access control.
 *
 */
public class AttributesExample {

    private static String SERVER_URL = "http://localhost:10035";
    private static String CATALOG_ID = "java-catalog";
    private static String REPOSITORY_ID = "attributesExample";
    private static String USERNAME = "test";
    private static String PASSWORD = "xyzzy";
    
    private static AGServer server;
    private static AGCatalog catalog;
    private static AGRepository repo;
    private static AGRepositoryConnection conn;
    
    private static void println(Object x) {
        System.out.println(x);
    }
    
    private static void printRows(String headerMsg, RepositoryResult<Statement> rows) throws Exception {
    	println(headerMsg);
    	int count = 0;
        while (rows.hasNext()) {
            println(rows.next());
            count++;
        }
        println("Number of results: " + count);
        rows.close();
    }
    
    /**
     * Create a fresh AG repository and return a connection object to it.
     * 
     * @return AGRepositoryConnection
     * @throws RepositoryException
     */
    private static AGRepositoryConnection setupRepository() throws RepositoryException {
    	
    	server = new AGServer(SERVER_URL, USERNAME, PASSWORD);
    	catalog = server.getCatalog(CATALOG_ID);
    	println("Creating fresh repository " + CATALOG_ID + "/" + REPOSITORY_ID);
    	catalog.deleteRepository(REPOSITORY_ID);
    	repo = catalog.createRepository(REPOSITORY_ID);
        repo.initialize();

        return conn = repo.getConnection();
    }
    
    /*
     * Define the attributes that will be used in this example. Attributes need only
     * be defined once when setting up a repository. When importing data containing
     * triple attributes, all attributes must first be defined or the import will fail.
     */
    private static void defineAttributes() throws Exception {
    
    	try {
    		conn.begin();
    		
	    	/*
	    	 * securityLevel describes the sensitivity level of the triple it
	    	 * is attached to.
	    	 * 
	    	 * securityLevel must be applied to all triples, only once.
	    	 * The acceptable values are: low, medium, and high
	    	 * 
	    	 * The following (ordered) relationship holds:
	    	 *    low < medium < high
	    	 */
	    	AttributeDefinition seclev = conn.new AttributeDefinition("securityLevel")
	    						.ordered(true)
	    						.allowedValue("low")
	    						.allowedValue("medium")
	    						.allowedValue("high")
	    						.minimum(1)
	    						.maximum(1)
	    						.add();
	    	
	    	/*
	    	 * department attributes indicate the department a triple is applicable
	    	 * to. Only the below 4 values are allowed, and zero or more can be assigned.
	    	 */
	    	AttributeDefinition dept = conn.new AttributeDefinition("department")
	    						.allowedValue("devel")
	    						.allowedValue("hr")
	    						.allowedValue("sales")
	    						.allowedValue("accounting")
	    						.add();
	    	
	    	/*
	    	 * accessTokens describe a set of arbitrary values that are associated with
	    	 * a given triple. As they are used in this example, they allow for finer
	    	 * grained filtering along an additional dimension. e.g. "Who, in a given
	    	 * department with the appropriate securityLevel can access the triple
	    	 * data associated with these accessTokens?"
	    	 * 
	    	 *  Similar to department, only the below 5 values are valid, and zero
	    	 *  or more can be assigned.
	    	 */
	    	AttributeDefinition access = conn.new AttributeDefinition("accessToken")
	    						.allowedValue("A")
	    						.allowedValue("B")
	    						.allowedValue("C")
	    						.allowedValue("D")
	    						.allowedValue("E")
	    						.add();
    	} finally {
    		conn.commit();
    	}
    }
    
    /*
     * Add a sample set of triples with various attributes based on the above attribute definitions.
     */
    private static void populateRepository() throws Exception {
    	AGValueFactory vf = conn.getRepository().getValueFactory();
    	
    	println("Adding sample triples with attributes to the repository.");
    	
    	try {
    		// start a transaction so all triples are committed at once.
    		conn.begin();
    	
    		// subject for our set of triples.
    		BNode s = vf.createBNode();
    	
    		String ex = "http://example.org/ontology/";
    	
    		// a set of predicates we will use.
    		URI pName = vf.createURI( ex + "name");
    		URI pSalary = vf.createURI(ex + "salary");
    		URI pDept = vf.createURI(ex + "department");
    		URI pInfractions = vf.createURI( ex + "infractions");
    	
    		// attributes for triples with general employee info
    		JSONObject infoAttrs = new JSONObject()
    				.put("securityLevel", "low")
    				.put("department", new JSONArray().put("devel").put("hr").put("accounting").put("sales"))
    				.put("accessToken", "A");
    	
    		// attributes for triples related to employee salary
    		JSONObject salaryAttrs = new JSONObject()
    				.put("securityLevel", "medium")
    				.put("department", new JSONArray().put("hr").put("accounting"))
    				.put("accessToken", "A");
    	
    		// attributes for triples related to personal/sensitive employee information.
    		JSONObject sensitiveAttrs = new JSONObject()
    				.put("securityLevel", "high")
    				.put("department", "hr")
    				.put("accessToken",  new JSONArray().put("D").put("E"));
    	
    		conn.add(s, pName, vf.createLiteral("Joe Smith"), infoAttrs);
    		conn.add(s, pDept, vf.createURI(ex, "ops"), infoAttrs);
    		conn.add(s, pSalary, vf.createLiteral(100000), salaryAttrs);
    		conn.add(s, pInfractions, vf.createURI("http://example.org/ontology/Infraction#",
    												"ExcessiveTardiness"),
    				 sensitiveAttrs);
    	} finally {
    		conn.commit();
    	}
    	
    	println("Total unfiltered triple count is: " + conn.size());
    	
    }
    
    /*
     * Define a static filter based on the attributes defined in defineAttributes().
     * 
     * For a triple to be visible:
     * - The user securityLevel must be >= the securityLevel attribute on a triple.
     * - The user department attributes must match at least one of the department
     *   attributes on a triple.
     * - The user access-token attributes must contain all accessToken attributes
     *   on a triple.
     */
    private static void defineStaticFilter() throws Exception {
    	try {
    		
    		conn.begin();
	    	conn.setStaticAttributeFilter(
	    			"(and (attribute>= user.securityLevel triple.securityLevel) " +
	    				 "(attribute-contains-one-of user.department triple.department) " +
	    				 "(attribute-contains-all-of user.accessToken triple.accessToken))");
    	} finally {
    		conn.commit();
    	}
    }
    
    private static void printVisibleTriples(String msg) throws Exception {
    	printRows(msg, conn.getStatements(null, null, null, false));
    }
    
    public static void main(String[] args) throws Exception {
    	setupRepository();
    	defineAttributes();
    	populateRepository();
    	
    	// define some user-attributes that we will use below.
    	String hrLowSecUser = "{ securityLevel: [ \"low\" ], department: [ \"hr\" ], accessToken: [ \"A\", \"B\" ] }";
    	String acctUser = "{ securityLevel: [ \"medium\" ], department: [ \"accounting\" ], accessToken: [ \"A\", \"C\" ] }";
    	String hrHighSecUser1 = "{ securityLevel: [ \"high\" ], department: [ \"hr\", \"devel\" ], accessToken: [ \"A\", \"B\" ] }";
    	String hrHighSecUser2 = "{ securityLevel: [ \"high\" ], department: [ \"hr\", \"accounting\" ], accessToken: [ \"A\", \"C\", \"D\", \"E\" ] }";
    	
    	// No filter is set, so all triples should be visible.
    	printVisibleTriples("Visible triples with no static filter set, and no user-attributes");
    	
    	// No filter, so setting user attributes has no effect. All triples are still visible.
    	try (UserAttributesContext ctxt = new UserAttributesContext(conn, hrLowSecUser)) {
    		printVisibleTriples("Visible with user attributes set, but no static filter.");
    	}
    	
    	defineStaticFilter();
    	
    	// Now that a filter is set, we can control the set of visible triples by setting user attributes.
    	
    	// only low security triples will be visible.
    	try (UserAttributesContext ctxt = new UserAttributesContext(conn, hrLowSecUser)) {
    		printVisibleTriples("Triples visible to hrLowSecUser.");
    	}
    	
    	// low and medium security triples will be visible.
    	try (UserAttributesContext ctxt = new UserAttributesContext(conn, acctUser)) {
    		printVisibleTriples("Triples visible to acctUser.");
    	}
    	
    	// high security triples will not be visible due to accessToken mismatch.
    	try (UserAttributesContext ctxt = new UserAttributesContext(conn, hrHighSecUser1)) {
    		printVisibleTriples("Triples visible to hrHighSecUser1.");
    	}
    	
    	// this user has sufficient attribute settings to see all triples.
    	try (UserAttributesContext ctxt = new UserAttributesContext(conn, hrHighSecUser2)) {
    		printVisibleTriples("Triples visible to hrHighSecUser2.");
    	}
    	
    	println("Example complete.");
    }   
}