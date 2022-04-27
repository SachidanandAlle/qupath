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

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.util.Pair;

public class RequestUtils {
	private final static Logger logger = LoggerFactory.getLogger(RequestUtils.class);

	public static String request(String method, String uri, String body) throws IOException {
		String monaiServer = Settings.serverURLProperty().get();
		String requestURI = monaiServer + uri;
		logger.info("MONAI Label Annotation - URL => " + requestURI);

		HttpURLConnection connection = (HttpURLConnection) new URL(requestURI).openConnection();
		connection.setRequestMethod(method);
		connection.setRequestProperty("Content-Type", "application/json");
		connection.setDoInput(true);
		connection.setDoOutput(true);

		if (body != null && !body.isEmpty()) {
			connection.getOutputStream().write(body.getBytes("UTF-8"));
		}
		return new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}

	public static String requestMultiPart(String method, String uri, Pair<String, File> file, String params) throws IOException {
		String monaiServer = Settings.serverURLProperty().get();
		String requestURI = monaiServer + uri;
		logger.info("MONAI Label Annotation - URL => " + requestURI);

		var builder = MultipartEntityBuilder.create();
		builder.addPart(file.getKey(), new FileBody(file.getValue()));
		builder.addTextBody("params", params);

		var request = method == "POST" ? new HttpPost(requestURI) : new HttpPut(requestURI);
		request.setEntity(builder.build());

		var response = HttpClientBuilder.create().build().execute(request);
		return new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
	}

}
