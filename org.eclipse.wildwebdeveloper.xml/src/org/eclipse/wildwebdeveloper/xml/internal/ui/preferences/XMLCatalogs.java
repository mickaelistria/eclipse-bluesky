/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.wildwebdeveloper.xml.internal.ui.preferences;

import static org.eclipse.wildwebdeveloper.xml.internal.ui.preferences.XMLPreferenceConstants.XML_PREFERENCES_CATAGLOGS;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.wildwebdeveloper.xml.internal.Activator;

public class XMLCatalogs {

	private static final String PLATFORM_PLUGIN_PATH = "platform:/plugin/";
	private static final Comparator<File> FILE_CASE_INSENSITIVE_ORDER = Comparator.comparing(File::getAbsolutePath, String.CASE_INSENSITIVE_ORDER);
	private static final File SYSTEM_CATALOG = Activator.getDefault().getStateLocation().append("system-catalog.xml").toFile();

	public static Set<File> getUserCatalogs(IPreferenceStore store) {
		Set<File> catalogs = new TreeSet<>(FILE_CASE_INSENSITIVE_ORDER);
		for (String filepath : store.getString(XML_PREFERENCES_CATAGLOGS.preferenceId).split(",")) {
			if (!filepath.isEmpty()) {
				catalogs.add(new File(filepath));
			}
		}
		return catalogs;
	}

	public static Set<File> getAllCatalogs(IPreferenceStore store) {
		Set<File> res = getUserCatalogs(store);
		res.add(getWTPExtensionCatalog());
		return res;
	}

	
	private static File getWTPExtensionCatalog() {
		StringBuilder catalogFile = new StringBuilder();
		catalogFile.append("<?xml version=\"1.0\"?>\n"
				+ "<!DOCTYPE catalog PUBLIC \"-//OASIS//DTD Entity Resolution XML Catalog V1.0//EN\" \"http://www.oasis-open.org/committees/entity/release/1.0/catalog.dtd\">\n"
				+ "<catalog xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\" prefer=\"public\">\n");
		Arrays.stream(Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.wst.xml.core.catalogContributions"))
			.filter(element -> "catalogContribution".equals(element.getName()))
			.flatMap(element -> Arrays.stream(element.getChildren("system")))
			.forEach(element -> {
				String namespace = element.getAttribute("systemId");
				String uri = element.getAttribute("uri");
				if (uri.startsWith(PLATFORM_PLUGIN_PATH)) {
					// ex : platform:/plugin/org.eclipse.xsd/cache/www.w3.org/2001/xml.xsd
					int start = PLATFORM_PLUGIN_PATH.length();
					int end = uri.indexOf("/", start);					
					String pluginId = uri.substring(start, end);
					String path = uri.substring(end, uri.length());
					try {
						uri = FileLocator.toFileURL(FileLocator.find(Platform.getBundle(pluginId), Path.fromPortableString(path))).toString();
					} catch (InvalidRegistryObjectException | IOException e) {
						Activator.getDefault().getLog().log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e));
					}
				} else if (!URI.create(uri).isAbsolute()) {
					try {
						uri = FileLocator.toFileURL(FileLocator.find(Platform.getBundle(element.getContributor().getName()), Path.fromPortableString(uri.toString()))).toString();
					} catch (InvalidRegistryObjectException | IOException e) {
						Activator.getDefault().getLog().log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e));
					}
				}
				if (namespace != null && uri != null) {
					catalogFile.append("<system systemId=\"")
						.append(namespace)
						.append("\" uri=\"")
						.append(uri)
						.append("\"/>\n");
				}
			});
		/* example
  <extension point="org.eclipse.wst.xml.core.catalogContributions">
    <catalogContribution>
      <system systemId="http://maven.apache.org/maven-v4_0_0.xsd"
              uri="xsd/maven-v4_0_0.xsd"/>
      <system systemId="http://maven.apache.org/xsd/maven-4.0.0.xsd"
              uri="xsd/maven-v4_0_0.xsd"/>
      <system systemId="http://maven.apache.org/xsd/settings-1.0.0.xsd"
              uri="xsd/settings-v1_0_0.xsd"/>
      <system systemId="http://maven.apache.org/xsd/profiles-1.0.0.xsd"
              uri="xsd/profiles-v1_0_0.xsd"/>
      <system systemId="http://maven.apache.org/xsd/archetype-1.0.0.xsd"
              uri="xsd/archetype-1.0.0.xsd"/>
      <system systemId="http://maven.apache.org/xsd/archetype-catalog-1.0.0.xsd"
              uri="xsd/archetype-catalog-1.0.0.xsd"/>
      <system systemId="http://maven.apache.org/xsd/archetype-descriptor-1.0.0.xsd"
              uri="xsd/archetype-descriptor-1.0.0.xsd"/>
    </catalogContribution>
  </extension>
		 */
		catalogFile.append("</catalog>");
		try {
			Files.writeString(SYSTEM_CATALOG.toPath(), catalogFile.toString());
		} catch (IOException e) {
			Activator.getDefault().getLog().log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e));
		}
		return SYSTEM_CATALOG;
	}

	public static void storeUserCatalogs(IPreferenceStore store, Set<File> catalogs) {
		StringBuilder catalogsStr = new StringBuilder();
		if (!catalogs.isEmpty()) {
			for (File f : catalogs) {
				catalogsStr.append(f.getAbsolutePath()).append(',');
			}
		}
		store.setValue(XML_PREFERENCES_CATAGLOGS.preferenceId, catalogsStr.toString());
	}

}
