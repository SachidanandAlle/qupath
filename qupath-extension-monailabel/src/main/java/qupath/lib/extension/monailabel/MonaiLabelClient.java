/*
Copyright (c) MONAI Consortium
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package qupath.lib.extension.monailabel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.google.gson.Gson;

import qupath.lib.geom.Point2;

public class MonaiLabelClient {
	private final static Logger logger = LoggerFactory.getLogger(MonaiLabelClient.class);

	public static class Model {
		public String type;
		public int dimension;
		public String description;
	}

	public static class ResponseInfo {
		public String name;
		public String description;
		public String version;
		public String[] labels;
		public Map<String, Model> models;
	}

	public static class InferParams {
		public List<List<Integer>> foreground = new ArrayList<>();
		public List<List<Integer>> background = new ArrayList<>();

		public void addClicks(ArrayList<Point2> clicks, boolean f) {
			List<List<Integer>> t = f ? foreground : background;
			for (int i = 0; i < clicks.size(); i++) {
				int x = (int) clicks.get(i).getX();
				int y = (int) clicks.get(i).getY();
				t.add(Arrays.asList(new Integer[] { x, y }));
			}
		}
	};

	public static class RequestInfer {
		public int level = 0;
		public int[] location = { 0, 0 };
		public int[] size = { 0, 0 };
		public int[] tile_size = { 2048, 2048 };
		public int min_poly_area = 30;
		public InferParams params = new InferParams();
	};

	public static ResponseInfo info() {
		String uri = "/info/";
		String res = RequestUtils.request("GET", uri, null);
		logger.info("MONAILabel Annotation - INFO => " + res);

		return new Gson().fromJson(res, ResponseInfo.class);
	}

	public static Document infer(String model, String image, RequestInfer req)
			throws SAXException, IOException, ParserConfigurationException {

		String uri = "/infer/wsi/" + model + "?image=" + image + "&output=asap";

		Gson gson = new Gson();
		String jsonBody = gson.toJson(req, RequestInfer.class);
		logger.info("MONAILabel Annotation - BODY => " + jsonBody);

		String response = RequestUtils.request("POST", uri, jsonBody);

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputStream inputStream = new ByteArrayInputStream(response.getBytes());
		Document dom = builder.parse(inputStream);
		return dom;
	}

}
