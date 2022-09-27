/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.scripting.languages;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.ServiceLoader;

import javax.script.ScriptContext;
import javax.script.ScriptException;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.WebViews;
import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;

/**
 * Class for the representation of JSON syntax in QuPath.
 * <p>
 * This class stores the QuPath implementation of Markdown syntaxing and a dummy plain auto-completion.
 * @author Pete Bankhead (based on Melvin Gelbard's code)
 * @since v0.4.0
 */
public class MarkdownLanguage extends ScriptLanguage implements RunnableLanguage {
	
	/**
	 * Instance of this language. Can't be final because of {@link ServiceLoader}.
	 */
	private static MarkdownLanguage INSTANCE;
	
	private ScriptSyntax syntax;
	private ScriptAutoCompletor completor;
	
	/**
	 * Constructor for JSON language. This constructor should never be 
	 * called. Instead, use the static {@link #getInstance()} method.
	 * <p>
	 * Note: this has to be public for the {@link ServiceLoader} to work.
	 */
	public MarkdownLanguage() {
		super("Markdown", new String[]{".md", ".markdown"});
		this.syntax = PlainSyntax.getInstance();
		this.completor = new PlainAutoCompletor();
		
		if (INSTANCE != null)
			throw new UnsupportedOperationException("Language classes cannot be instantiated more than once!");
		
		// Because of ServiceLoader, have to assign INSTANCE here.
		MarkdownLanguage.INSTANCE = this;
	}

	/**
	 * Get the static instance of this class.
	 * @return instance
	 */
	public static MarkdownLanguage getInstance() {
		return INSTANCE;
	}
	
	@Override
	public ScriptSyntax getSyntax() {
		return syntax;
	}

	@Override
	public ScriptAutoCompletor getAutoCompletor() {
		return completor;
	}
	
	private static Stage stage;
	private static WebView webview;
	
	private void showHtml(String html) {
		var qupath = QuPathGUI.getInstance();
		if (qupath == null || qupath.getStage() == null)
			return;
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showHtml(html));
			return;			
		}
		if (webview == null) {
			webview = WebViews.create(true);
			stage = new Stage();
			stage.setScene(new Scene(webview));
			stage.setTitle("Rendered markdown");
			stage.initOwner(QuPathGUI.getInstance().getStage());
		}
		webview.getEngine().loadContent(html);
		if (!stage.isShowing())
			stage.show();
		else
			stage.toFront();
	}

	@Override
	public Object executeScript(String script, Project<BufferedImage> project, ImageData<BufferedImage> imageData,
			Collection<Class<?>> defaultImports, Collection<Class<?>> defaultStaticImports, ScriptContext context)
			throws ScriptException {
		
		try {
			var doc = Parser.builder().build().parse(script);
			var html = HtmlRenderer.builder().build().render(doc);
			showHtml(html);
			return html;
		} catch (Exception e) {
			throw new ScriptException(e);
		}
	}

	@Override
	public String getImportStatements(Collection<Class<?>> classes) {
		return null;
	}

	@Override
	public String getStaticImportStatments(Collection<Class<?>> classes) {
		return null;
	}
}
