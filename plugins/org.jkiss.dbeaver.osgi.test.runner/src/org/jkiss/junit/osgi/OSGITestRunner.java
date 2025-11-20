/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,ManifestElement
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.junit.osgi;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.Log;
import org.jkiss.junit.osgi.annotation.RunWithApplication;
import org.jkiss.junit.osgi.annotation.RunWithProduct;
import org.jkiss.junit.osgi.annotation.RunnerProxy;
import org.jkiss.junit.osgi.behaviors.IAsyncApplication;
import org.jkiss.utils.Pair;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
// import org.osgi.framework.Bundle;
// import org.osgi.framework.launch.Framework;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <h2>OSGITestRunner</h2>
 * <p>
 *     The class is responsible for running the OSGi tests inside IDEA.
 *     It does by starting the OSGi framework and loading all the required bundles.
 *     If OSGI environment is already running, it will not start a new one.
 *     <li>{@link RunWithProduct} annotation to specify the product to run the test in.</li>
 *     <li>{@link RunnerProxy} to specify the runner which should be executed in OSGI environment.</li>
 *     <li>{@link RunWithApplication} to specify the application to run the test in.</li>
 *     <br>
 *     Should allow debugging of the tests in the IDEA.
 * </p>
 */
public class OSGITestRunner extends BlockJUnit4ClassRunner {

    public static final Pattern startLevel = Pattern.compile("@(\\d+):start");
    private static final Log log = Log.getLog(OSGITestRunner.class);
    private static final boolean DEBUG_BUNDLE_LAUNCH = false;
    private final Class<?> testClass;
    // private Framework framework;
    private Path productPath;

    private String testBundleName;
    // private Bundle testBundle;
    // private String appRegistryName;
    // private String appBundleName;
    // private String[] args;
    private Object runnerProxy = null;

    public OSGITestRunner(
        @NotNull Class<? extends IAsyncApplication> testClass
    ) throws Exception {
        super(testClass);
        if (testClass.getAnnotation(RunnerProxy.class) == null) {
            throw new IllegalArgumentException("RunnerProxy annotation not found");
        }
        this.testClass = testClass;
        createProxyInSameClassloader();
    }

    // private void getAppBundleFromAnnotation() {
    //     if (testClass.getAnnotation(RunWithApplication.class) != null) {
    //         // RunWithApplication annotation = testClass.getAnnotation(RunWithApplication.class);
    //         // this.appRegistryName = annotation.registryName();
    //         // this.appBundleName = annotation.bundleName();
    //         // this.args = annotation.args();
    //     } else {
    //         throw new IllegalArgumentException("Application not found");
    //     }
    // }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        super.filter(filter);
        try {
            runnerProxy.getClass().getMethod("filter", Filter.class).invoke(runnerProxy, filter);
        } catch (Exception e) {
            log.error("Error applying filter to proxy", e);
        }
    }

    @Override
    public void run(RunNotifier notifier) {
        launchInExistingOSGI(notifier);
    }

    private Path findProduct() {
        if (testClass.getAnnotation(RunWithProduct.class) != null) {
            RunWithProduct annotation = testClass.getAnnotation(RunWithProduct.class);
            String product = annotation.value();
            Path workspace = Path.of(findWorkspaceDir().toString());
            return workspace.resolve(product);
        } else {
            throw new IllegalArgumentException("Product not found");
        }
    }

    private static Path findWorkspaceDir() {
        Path workPath = Paths.get("").toAbsolutePath();
        Path currentPath = workPath.toAbsolutePath();
        while (currentPath != null) {
            Path potentialWorkspaceDir = currentPath.resolve("dbeaver-workspace/products");
            if (Files.exists(potentialWorkspaceDir)) {
                return workPath.relativize(potentialWorkspaceDir);
            }
            currentPath = currentPath.getParent();
        }
        throw new IllegalStateException("dbeaver-workspace/products directory not found");
    }

    private void launchInExistingOSGI(RunNotifier notifier) {
        try {
            if (testClass.getAnnotation(RunnerProxy.class) != null) {
                Arrays.stream(runnerProxy.getClass().getMethods()).filter(it -> it.getName().equals("run")).findFirst().orElseThrow()
                    .invoke(runnerProxy, notifier);
            }
        } catch (Throwable throwable) {
            log.error("An error occurred while running the test", throwable);
        }
    }

    private void createProxyInSameClassloader(
    ) throws NoSuchMethodException, ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Constructor<?> constructor = testClass
            .getClassLoader()
            .loadClass(testClass.getAnnotation(RunnerProxy.class).value().getName())
            .getConstructor(Class.class);
        runnerProxy = constructor.newInstance(testClass);
    }
}

