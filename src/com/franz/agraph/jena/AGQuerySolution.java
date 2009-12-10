package com.franz.agraph.jena;

import java.util.Iterator;

import org.openrdf.query.BindingSet;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

public class AGQuerySolution implements QuerySolution {

	private final BindingSet bs;
	private final AGModel model;
	
	public AGQuerySolution(BindingSet bs, AGModel model) {
		this.bs = bs;
		this.model = model;
	}

	@Override
	public boolean contains(String varName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public RDFNode get(String varName) {
		return model.asRDFNode(AGNodeFactory.asNode(bs.getValue(varName)));
	}

	@Override
	public Literal getLiteral(String varName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Resource getResource(String varName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterator<String> varNames() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String toString() {
		return bs.toString();
	}
}
