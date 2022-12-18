import fxlauncher.DefaultUIProvider;

module fxlauncher {
	exports fxlauncher;

	requires java.logging;
	requires javafx.base;
	requires javafx.controls;
	requires javafx.fxml;
	requires javafx.graphics;
	requires jakarta.xml.bind;
	requires javafx.web;
	requires static com.google.auto.service;
	
	opens fxlauncher to jakarta.xml.bind;
	
	// provides fxlauncher.UIProvider with fxlauncher.DefaultUIProvider;
	
	uses fxlauncher.UIProvider;
}