/**
 * Copyright 2008 - 2015 The Loon Game Engine Authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * @project loon
 * @author cping
 * @email：javachenpeng@yahoo.com
 * @version 0.5
 */
package loon.opengl;

import java.util.Iterator;
import java.util.StringTokenizer;

import loon.BaseIO;
import loon.LRelease;
import loon.LSystem;
import loon.LTexture;
import loon.LTextureBatch.Cache;
import loon.canvas.LColor;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.ObjectMap.Entries;
import loon.utils.ObjectMap.Entry;
import loon.utils.TArray;

// AngelCode图像字体专用类(因为仅处理限定范围内的字体，此类速度会比较早前版本中提供的文字渲染类更快，
// 但缺点在于，没有提供图像的文字不能被渲染).
public class BMFont implements LRelease {

	private static final int DEFAULT_MAX_CHAR = 255;

	private ObjectMap<String, Display> displays;

	private int lazyHashCode = 1;

	private LTexture displayList;

	private CharDef[] chars;

	private int lineHeight;

	private boolean isClose;

	private String info, common, page;

	private class Display {

		String text;

		Cache cache;

		int width;

		int height;
	}

	private class CharDef {

		short id;

		short tx;

		short ty;

		short width;

		short height;

		short xoffset;

		short yoffset;

		short advance;

		short[] kerning;

		public void draw(float x, float y) {
			if (isClose) {
				return;
			}
			displayList.draw(x + xoffset, y + yoffset, width, height, tx, ty,
					tx + width, ty + height);
		}

		public int getKerning(int point) {
			if (kerning == null) {
				return 0;
			}
			int low = 0;
			int high = kerning.length - 1;
			while (low <= high) {
				int midIndex = (low + high) >>> 1;
				int value = kerning[midIndex];
				int foundCodePoint = value & 0xff;
				if (foundCodePoint < point) {
					low = midIndex + 1;
				} else if (foundCodePoint > point) {
					high = midIndex - 1;
				} else {
					return value >> 8;
				}
			}
			return 0;
		}
	}

	public BMFont(String file, LTexture image) throws Exception {
		this.displayList = image;
		this.parse(BaseIO.loadText(file));
	}

	public BMFont(String file, String imgFile) throws Exception {
		this.displayList = BaseIO.loadTexture(imgFile);
		this.parse(BaseIO.loadText(file));
	}

	private void parse(String text) throws Exception {
		if (displays == null) {
			displays = new ObjectMap<String, Display>(DEFAULT_MAX_CHAR);
		} else {
			displays.clear();
		}
		StringTokenizer br = new StringTokenizer(text, LSystem.LS);
		info = br.nextToken();
		common = br.nextToken();
		page = br.nextToken();

		ObjectMap<Short, TArray<Short>> kerning = new ObjectMap<Short, TArray<Short>>(
				64);
		TArray<CharDef> charDefs = new TArray<CharDef>(DEFAULT_MAX_CHAR);

		int maxChar = 0;
		boolean done = false;
		for (; !done;) {
			String line = br.nextToken();
			if (line == null) {
				done = true;
			} else {
				if (line.startsWith("chars c")) {
				} else if (line.startsWith("char")) {
					CharDef def = parseChar(line);
					if (def != null) {
						maxChar = MathUtils.max(maxChar, def.id);
						charDefs.add(def);
					}
				}
				if (line.startsWith("kernings c")) {
				} else if (line.startsWith("kerning")) {
					StringTokenizer tokens = new StringTokenizer(line, " =");
					tokens.nextToken();
					tokens.nextToken();
					short first = Short.parseShort(tokens.nextToken());
					tokens.nextToken();
					int second = Integer.parseInt(tokens.nextToken());
					tokens.nextToken();
					int offset = Integer.parseInt(tokens.nextToken());
					TArray<Short> values = kerning.get(new Short(first));
					if (values == null) {
						values = new TArray<Short>();
						kerning.put(new Short(first), values);
					}
					values.add(new Short((short) ((offset << 8) | second)));
				}
			}
		}

		this.chars = new CharDef[maxChar + 1];

		for (Iterator<CharDef> iter = charDefs.iterator(); iter.hasNext();) {
			CharDef def = iter.next();
			chars[def.id] = def;
		}

		for (Entries<Short, TArray<Short>> iter = kerning.entries(); iter.hasNext();) {
			Entry<Short, TArray<Short>> entry = iter.next();
			short first = entry.key;
			TArray<Short> valueList = entry.value;
			short[] valueArray = new short[valueList.size];
			int i = 0;
			for (Iterator<Short> valueIter = valueList.iterator(); valueIter
					.hasNext(); i++) {
				valueArray[i] = (valueIter.next()).shortValue();
			}
			chars[first].kerning = valueArray;
		}
	}

	private CharDef parseChar(final String line) throws Exception {
		CharDef def = new CharDef();
		StringTokenizer tokens = new StringTokenizer(line, " =");

		tokens.nextToken();
		tokens.nextToken();
		def.id = Short.parseShort(tokens.nextToken());
		if (def.id < 0) {
			return null;
		}
		if (def.id > DEFAULT_MAX_CHAR) {
			throw new Exception(def.id + " > " + DEFAULT_MAX_CHAR);
		}

		tokens.nextToken();
		def.tx = Short.parseShort(tokens.nextToken());
		tokens.nextToken();
		def.ty = Short.parseShort(tokens.nextToken());
		tokens.nextToken();
		def.width = Short.parseShort(tokens.nextToken());
		tokens.nextToken();
		def.height = Short.parseShort(tokens.nextToken());
		tokens.nextToken();
		def.xoffset = Short.parseShort(tokens.nextToken());
		tokens.nextToken();
		def.yoffset = Short.parseShort(tokens.nextToken());
		tokens.nextToken();
		def.advance = Short.parseShort(tokens.nextToken());

		if (def.id != (short) ' ') {
			lineHeight = MathUtils.max(def.height + def.yoffset, lineHeight);
		}

		return def;
	}

	public void drawString(float x, float y, String text) {
		drawString(x, y, text, null);
	}

	public void drawString(float x, float y, String text, LColor col) {
		drawBatchString(x, y, text, col, 0, text.length() - 1);
	}

	private void drawBatchString(float tx, float ty, String text, LColor c,
			int startIndex, int endIndex) {

		if (isClose) {
			return;
		}

		if (displays.size > DEFAULT_MAX_CHAR) {
			displays.clear();
		}

		lazyHashCode = 1;

		if (c != null) {
			lazyHashCode = LSystem.unite(lazyHashCode, c.r);
			lazyHashCode = LSystem.unite(lazyHashCode, c.g);
			lazyHashCode = LSystem.unite(lazyHashCode, c.b);
			lazyHashCode = LSystem.unite(lazyHashCode, c.a);
		}

		String key = text + lazyHashCode;

		Display display = displays.get(key);

		if (display == null) {

			int x = 0, y = 0;

			displayList.glBegin();
			displayList.setBatchPos(tx, ty);

			if (c != null) {
				displayList.setImageColor(c);
			}

			CharDef lastCharDef = null;
			char[] data = text.toCharArray();
			for (int i = 0; i < data.length; i++) {
				int id = data[i];
				if (id == '\n') {
					x = 0;
					y += getLineHeight();
					continue;
				}
				if (id >= chars.length) {
					continue;
				}
				CharDef charDef = chars[id];
				if (charDef == null) {
					continue;
				}

				if (lastCharDef != null) {
					x += lastCharDef.getKerning(id);
				}
				lastCharDef = charDef;

				if ((i >= startIndex) && (i <= endIndex)) {
					charDef.draw(x, y);
				}

				x += charDef.advance;
			}

			if (c != null) {
				displayList.setImageColor(LColor.white);
			}

			displayList.glEnd();

			display = new Display();

			display.cache = displayList.newBatchCache();
			display.text = text;
			display.width = 0;
			display.height = 0;

			displays.put(key, display);

		} else if (display.cache != null) {
			display.cache.x = tx;
			display.cache.y = ty;
			displayList.postCache(display.cache);
		}

	}

	public int getHeight(String text) {
		if (text == null) {
			return 0;
		}
		Display display = null;
		for (Display d : displays.values()) {
			if (d != null && text.equals(d.text)) {
				display = d;
				break;
			}
		}
		if (display != null && display.height != 0) {
			return display.height;
		}
		if (display == null) {
			display = new Display();
		}
		int lines = 0;
		for (int i = 0; i < text.length(); i++) {
			int id = text.charAt(i);
			if (id == '\n') {
				lines++;
				display.height = 0;
				continue;
			}
			if (id == ' ') {
				continue;
			}
			CharDef charDef = chars[id];
			if (charDef == null) {
				continue;
			}
			display.height = MathUtils.max(charDef.height + charDef.yoffset,
					display.height);
		}
		display.height += lines * getLineHeight();
		return display.height;
	}

	public int getWidth(String text) {
		if (text == null) {
			return 0;
		}
		Display display = null;
		for (Display d : displays.values()) {
			if (d != null && text.equals(d.text)) {
				display = d;
				break;
			}
		}
		if (display != null && display.width != 0) {
			return display.width;
		}
		if (display == null) {
			display = new Display();
		}
		int width = 0;
		CharDef lastCharDef = null;
		for (int i = 0, n = text.length(); i < n; i++) {
			int id = text.charAt(i);
			if (id == '\n') {
				width = 0;
				continue;
			}
			if (id >= chars.length) {
				continue;
			}
			CharDef charDef = chars[id];
			if (charDef == null) {
				continue;
			}
			if (lastCharDef != null) {
				width += lastCharDef.getKerning(id);
			}
			lastCharDef = charDef;
			if (i < n - 1) {
				width += charDef.advance;
			} else {
				width += charDef.width;
			}
			display.width = MathUtils.max(display.width, width);
		}

		return display.width;
	}

	public String getCommon() {
		return common;
	}

	public String getInfo() {
		return info;
	}

	public String getPage() {
		return page;
	}

	public int getLineHeight() {
		return lineHeight;
	}

	public void close() {
		this.isClose = true;
		if (displayList != null) {
			displayList.close();
			displayList = null;
		}
		if (displays != null) {
			for (Display d : displays.values()) {
				if (d != null && d.cache != null) {
					d.cache.close();
					d.cache = null;
				}
			}
			displays.clear();
			displays = null;
		}
	}

}
