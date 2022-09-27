/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.scripting.richtextfx;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.layout.Region;
import qupath.lib.gui.scripting.ScriptEditorControl;

/**
 * Code area (e.g. RichTextFX) for writing code.
 * @author Pete Bankhead
 */
public class CodeAreaControl implements ScriptEditorControl {
	
	private VirtualizedScrollPane<CodeArea> scrollpane;
	private CodeArea textArea;
	private StringProperty textProperty = new SimpleStringProperty();
	
	CodeAreaControl(final CodeArea codeArea) {
		this.textArea = codeArea;
		textArea.textProperty().addListener((o, v, n) -> textProperty.set(n));
		textProperty.addListener((o, v, n) -> {
			if (n.equals(textArea.getText()))
				return;
			textArea.clear();
			textArea.insertText(0, n);
		});
		scrollpane = new VirtualizedScrollPane<>(textArea);
	}
	
	@Override
	public StringProperty textProperty() {
		return textProperty;
	}

	@Override
	public void setText(String text) {
		textArea.clear();
		textArea.insertText(0, text);
	}

	@Override
	public String getText() {
		return textArea.getText();
	}

	@Override
	public ObservableValue<String> selectedTextProperty() {
		return textArea.selectedTextProperty();
	}

	@Override
	public String getSelectedText() {
		return textArea.getSelectedText();
	}

	@Override
	public Region getControl() {
		return scrollpane;
	}

	@Override
	public boolean isUndoable() {
		return textArea.isUndoAvailable();
	}

	@Override
	public boolean isRedoable() {
		return textArea.isRedoAvailable();
	}

	@Override
	public void undo() {
		textArea.undo();
	}

	@Override
	public void redo() {
		textArea.redo();
	}

	@Override
	public void copy() {
		textArea.copy();
	}

	@Override
	public void cut() {
		textArea.cut();
	}

	@Override
	public void paste(String text) {
		if (text != null)
			textArea.replaceSelection(text);
	}
	
	@Override
	public void appendText(final String text) {
		textArea.appendText(text);
	}

	@Override
	public void clear() {
		textArea.clear();
	}
	
	@Override
	public int getCaretPosition() {
		return textArea.getCaretPosition();
	}
	
	@Override
	public void insertText(int pos, String text) {
		textArea.insertText(pos, text);
	}
	
	@Override
	public void deleteText(int startIdx, int endIdx) {
		textArea.deleteText(startIdx, endIdx);
	}
	
	@Override
	public ReadOnlyBooleanProperty focusedProperty() {
		return textArea.focusedProperty();
	}

	@Override
	public void deselect() {
		textArea.deselect();
	}

	@Override
	public IndexRange getSelection() {
		return textArea.getSelection();
	}

	@Override
	public void selectRange(int startIdx, int endIdx) {
		textArea.selectRange(startIdx, endIdx);
	}

	@Override
	public void setPopup(ContextMenu menu) {
		textArea.setContextMenu(menu);
	}
	
	@Override
	public BooleanProperty wrapTextProperty() {
		return textArea.wrapTextProperty();
	}

	@Override
	public void positionCaret(int index) {
		textArea.moveTo(index);
}

	@Override
	public void requestFollowCaret() {
		textArea.requestFollowCaret();
	}
}
