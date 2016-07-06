/******************************************************************************
** Copyright (c) 2008-2016 Franz Inc.
** All rights reserved. This program and the accompanying materials
** are made available under the terms of the Eclipse Public License v1.0
** which accompanies this distribution, and is available at
** http://www.eclipse.org/legal/epl-v10.html
******************************************************************************/

package com.franz.agraph.http.handler;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.httpclient.HttpMethod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.franz.agraph.http.exception.AGHttpException;

public class AGJSONHandler extends AGResponseHandler {

	private JSONObject result;
	private JSONArray resultList;
	
	public AGJSONHandler() {
		super("application/json");
	}

	/**
	 * Parse http response as a JSON object. We must invoke different parsers if the
	 * response contains an array or an object.
	 */
	@Override
	public void handleResponse(HttpMethod method) throws IOException, AGHttpException {
		/* TODO: server sometimes responds with text/plain, not application/json
		String mimeType = getResponseMIMEType(method);
		if (!mimeType.equals(getRequestMIMEType())) {
			throw new AGHttpException("unexpected response MIME type: " + mimeType);
		}*/

		try {
			InputStream response = getInputStream(method);
			String resp = streamToString(response);
			
			/*
			 *  try parsing responses as a JSONArray first, and then a JSONObject if that fails.
			 *  This method is imperfect, since it will misreport syntax errors generated when
			 *  parsing Arrays, by reparsing them as Objects instead.
			 *  
			 *  It is, however, better than scanning the string for enclosing braces.
			 *  We should consider using a more flexible JSON library.
			 */
			try {
				resultList = new JSONArray(resp);
			} catch (JSONException e) {
				result = new JSONObject(resp);
			}
			
		} catch (JSONException e) {
			throw new AGHttpException(e);
		}
	}	
	
	/**
	 * Fetch the JSONObject parsed from the response.
	 * 
	 * @return JSONObject
	 * @throws JSONException, if a JSONArray is instead available.
	 */
	public JSONObject getResult() throws JSONException {
		if (resultList != null) {
			throw new JSONException("Cannot return JSONArray from getResult(). use getArrayResult() instead.");
		} else {
			return result;
		}
	}
	
	/**
	 * Fetch the JSONArray parsed from the response. If the value parsed is a JSONObject, return
	 * a JSONArray with the JSONObject as its only element.
	 * 
	 * @return JSONArray
	 */
	public JSONArray getArrayResult() throws JSONException {
		if (result != null) {
			throw new JSONException("Cannot return JSONObject from getArrayResult(). Use getResult() instead.");
		} else {
			return resultList;
		}
	}
}
