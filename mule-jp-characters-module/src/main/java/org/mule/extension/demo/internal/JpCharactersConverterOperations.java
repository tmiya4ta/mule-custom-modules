package org.mule.extension.demo.internal;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;

import com.mariten.kanatools.KanaConverter;

/**
 * Japanese character conversion operations.
 * Converts between half-width (hankaku) and full-width (zenkaku) characters
 * including katakana, ASCII letters, numbers, and spaces.
 */
public class JpCharactersConverterOperations {

    /**
     * Converts half-width (hankaku) characters to full-width (zenkaku).
     * Covers: katakana, ASCII, letters, numbers, spaces.
     */
    @DisplayName("Hankaku -> Zenkaku")
    @MediaType(value = ANY, strict = false)
    public String convertHankakuToZenkaku(String inputText) {
	if (inputText == null || inputText.isEmpty()) {
	    return inputText;
	}
	int opFlags = KanaConverter.OP_HAN_KATA_TO_ZEN_KATA
	    | KanaConverter.OP_HAN_ASCII_TO_ZEN_ASCII
	    | KanaConverter.OP_HAN_LETTER_TO_ZEN_LETTER
	    | KanaConverter.OP_HAN_NUMBER_TO_ZEN_NUMBER
	    | KanaConverter.OP_HAN_SPACE_TO_ZEN_SPACE;
	return KanaConverter.convertKana(inputText, opFlags);
    }

    /**
     * Converts full-width (zenkaku) characters to half-width (hankaku).
     * Covers: katakana, ASCII, letters, numbers, spaces.
     */
    @DisplayName("Zenkaku -> Hankaku")
    @MediaType(value = ANY, strict = false)
    public String convertZenkakuToHankaku(String inputText) {
	if (inputText == null || inputText.isEmpty()) {
	    return inputText;
	}
	int opFlags = KanaConverter.OP_ZEN_KATA_TO_HAN_KATA
	    | KanaConverter.OP_ZEN_ASCII_TO_HAN_ASCII
	    | KanaConverter.OP_ZEN_LETTER_TO_HAN_LETTER
	    | KanaConverter.OP_ZEN_NUMBER_TO_HAN_NUMBER
	    | KanaConverter.OP_ZEN_SPACE_TO_HAN_SPACE;
	return KanaConverter.convertKana(inputText, opFlags);
    }
}
