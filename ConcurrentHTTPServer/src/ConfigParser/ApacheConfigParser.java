/*
 * Copyright 2013 Stackify
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package ConfigParser;

import java.io.*;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for Apache httpd configuration files.
 * 
 * <p>
 * This parser reads the configuration file line by line and builds a tree of the configuration.
 * 
 * @author jleacox
 * 
 */
public class ApacheConfigParser {
	private static final String commentRegex = "#.*";
	private static final String directiveRegex = "([^\\s]+)\\s*(.+)";
	private static final String sectionOpenRegex = "<([^/\\s>]+)\\s*([^>]+)?>";
	private static final String sectionCloseRegex = "</([^\\s>]+)\\s*>";

	private static final Matcher commentMatcher = Pattern.compile(commentRegex).matcher("");
	private static final Matcher directiveMatcher = Pattern.compile(directiveRegex).matcher("");
	private static final Matcher sectionOpenMatcher = Pattern.compile(sectionOpenRegex).matcher("");
	private static final Matcher sectionCloseMatcher = Pattern.compile(sectionCloseRegex).matcher("");

	public ApacheConfigParser() {
	}

	/**
	 * Parses an Apache httpd configuration file into a configuration tree.
	 * 
	 * <p>
	 * Each directive will be a leaf node in the tree, while configuration sections will be nodes with additional child
	 * configuration nodes
	 * 
	 * @param inputStream
	 *            the configuration file
	 * @return a {@link ConfigNode tree} representing the Apache configuration
	 * @throws IOException
	 *             if there is an error reading the configuration
	 */
	public static ConfigNode parse(InputStream inputStream) throws IOException {
		if (inputStream == null) {
			throw new NullPointerException("inputStream: null");
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		String line;
		ConfigNode currentNode = ConfigNode.createRootNode();
		ConfigNode firstPortNode = ConfigNode.createRootNode();

		HashMap<String, Boolean> firstMap = new HashMap<>();
		boolean first = false;
		boolean firstDone = false;

		while ((line = reader.readLine()) != null) {
			if (commentMatcher.reset(line).find()) {
				continue;
			} else if (sectionOpenMatcher.reset(line).find()) {
				String name = sectionOpenMatcher.group(1);
				String content = sectionOpenMatcher.group(2);
				ConfigNode sectionNode = ConfigNode.createChildNode(name, content, currentNode);
				if (!firstMap.containsKey(content)) {
					firstMap.put(content, true);
					firstPortNode = ConfigNode.createChildNode(name, content, currentNode);
					first = true;
					firstDone = false;
				}
				currentNode = sectionNode;
			} else if (sectionCloseMatcher.reset(line).find()) {
				currentNode = currentNode.getParent();
				if (firstDone)
					first = false;
			} else if (directiveMatcher.reset(line).find()) {
				String name = directiveMatcher.group(1);
				String content = directiveMatcher.group(2);
				ConfigNode.createChildNode(name, content, currentNode);
				if (first) {
					if (name.equals("ServerName")) {
						ConfigNode.createChildNode(name, "First", firstPortNode);
					} else if (name.equals("DocumentRoot")) {
						ConfigNode.createChildNode(name, content, firstPortNode);
					}
					firstDone = true;
				}
				
			}
		}

		return currentNode;
	}

	public static ServerConfigObject getServerConfigFrom(String fileName) {
		ServerConfigObject serverConfig = new ServerConfigObject();
		InputStream inputStream = null;

		try {
			inputStream = new FileInputStream(new File(fileName));
			int port = 0;
			ConfigNode config = parse(inputStream);
			for (ConfigNode child : config.getChildren()) {
				String serverName = null, documentRoot = null;

				if (child.getName().equals("Listen")) {
					port = Integer.parseInt(child.getContent());
					serverConfig.addPort(port);
					continue;
				} else if (child.getName().equals("nSelectLoops")) {
					int loop = Integer.parseInt(child.getContent());
					serverConfig.setnSelectLoops(loop);
					continue;
				} else if (child.getName().equals("VirtualHost")) {
					String portString = child.getContent();
					port = Integer.parseInt(portString.substring(portString.lastIndexOf(':') + 1));
				}

				for (ConfigNode entry : child.getChildren()) {
					if (entry.getName().equals("ServerName")) {
						serverName = entry.getContent();
					} else if (entry.getName().equals("DocumentRoot")) {
						documentRoot = entry.getContent();
					}
				}

				if (child.getName().equals("First")) {
					serverConfig.addMapping(child.getContent(), documentRoot, port);
				}

				if (serverName != null && documentRoot != null) {
					serverConfig.addMapping(serverName, documentRoot, port);
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
        }
		return serverConfig;
    }
}
