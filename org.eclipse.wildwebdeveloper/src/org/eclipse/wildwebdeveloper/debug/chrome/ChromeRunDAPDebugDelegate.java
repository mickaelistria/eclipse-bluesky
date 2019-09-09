/*******************************************************************************
 * Copyright (c) 2018, 2019 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *   Pierre-Yves B. - Issue #180 Wrong path to nodeDebug.js
 *******************************************************************************/
package org.eclipse.wildwebdeveloper.debug.chrome;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4e.debug.launcher.DSPLaunchDelegate;
import org.eclipse.swt.widgets.Display;
import org.eclipse.wildwebdeveloper.Activator;
import org.eclipse.wildwebdeveloper.InitializeLaunchConfigurations;
import org.eclipse.wildwebdeveloper.debug.AbstractDebugDelegate;

import com.google.gson.JsonObject;

public class ChromeRunDAPDebugDelegate extends DSPLaunchDelegate {

	static final String ID = "org.eclipse.wildwebdeveloper.launchConfiguration.chromeRunDebug"; //$NON-NLS-1$

	// see
	// https://github.com/Microsoft/vscode-node-debug/blob/master/src/node/nodeDebug.ts
	// LaunchRequestArguments
	static final String TRACE = "trace";
	static final String VERBOSE = "verbose";
	static final String RUNTIME_EXECUTABLE = "runtimeExecutable";

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		// user settings
		Map<String, Object> param = new HashMap<>();
		
		// File to debug 
		param.put("file", configuration.getAttribute(AbstractDebugDelegate.PROGRAM, "no program path defined"));
		// Chrome executable arguments
		String argsString = configuration.getAttribute(AbstractDebugDelegate.ARGUMENTS, "").trim(); //$NON-NLS-1$
		if (!argsString.isEmpty()) {
			Object[] args = Arrays.asList(argsString.split(" ")).stream() //$NON-NLS-1$
					.filter(s -> !s.trim().isEmpty()).toArray();
			if (args.length > 0) {
				param.put(AbstractDebugDelegate.ARGUMENTS, args);
			}
		}

		// Debug environment variables
		Map<String, String> env = configuration.getAttribute(AbstractDebugDelegate.ENV,
				Collections.emptyMap());
		if (!env.isEmpty()) {
			JsonObject envJson = new JsonObject();
			env.forEach((key, value) -> envJson.addProperty(key, value));
			param.put(AbstractDebugDelegate.ENV, envJson);
		}
		
		// Chrome working directory
		String cwd = configuration.getAttribute(AbstractDebugDelegate.CWD, "").trim(); //$NON-NLS-1$
		if (!cwd.isEmpty()) {
			param.put(AbstractDebugDelegate.CWD, cwd);
		}
		// workaround until
		// https://github.com/microsoft/vscode-node-debug2/commit/f2dfa4ca4026fb3e4f143a391270a03df8187b42#diff-d03a74f75ec189cbc7dd3d2e105fc9c9R625
		// is released in VSCode
		param.put(AbstractDebugDelegate.SOURCE_MAPS, false);
		
		// TODO: Let user point to the location of their chrome executable
		param.put(RUNTIME_EXECUTABLE, findChromeLocation());
		
		if (configuration.getAttribute(VERBOSE, false)) {
			param.put(TRACE, VERBOSE);
		}

		try {
			URL fileURL = FileLocator.toFileURL(
					getClass().getResource("/language-servers/chrome-debug-adapter/package/out/src/chromeDebug.js"));
			// "/language-servers/node_modules/vscode-firefox-debug/out/adapter/firefoxDebugAdapter.js"
			File file = new File(fileURL.getPath());
			List<String> debugCmdArgs = Collections.singletonList(file.getAbsolutePath());

			DSPLaunchDelegateLaunchBuilder builder = new DSPLaunchDelegateLaunchBuilder(configuration, mode, launch,
					monitor);
			builder.setLaunchDebugAdapter(InitializeLaunchConfigurations.getNodeJsLocation(), debugCmdArgs);
			builder.setMonitorDebugAdapter(configuration.getAttribute(DSPPlugin.ATTR_DSP_MONITOR_DEBUG_ADAPTER, false));
			builder.setDspParameters(param);

			super.launch(builder);
		} catch (IOException e) {
			IStatus errorStatus = new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e);
			Activator.getDefault().getLog().log(errorStatus);
			ErrorDialog.openError(Display.getDefault().getActiveShell(), "Debug error", e.getMessage(), errorStatus); //$NON-NLS-1$
		}
	}

	private String findChromeLocation() {
		String res = InitializeLaunchConfigurations.which("chromium-browser");
		if (res == null) {
			res = InitializeLaunchConfigurations.which("google-chrome-stable");
		}
		return res;
	}

}