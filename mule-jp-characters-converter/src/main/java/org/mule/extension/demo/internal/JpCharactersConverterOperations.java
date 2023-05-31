package org.mule.extension.demo.internal;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;

import com.mariten.kanatools.KanaConverter;

/**
 * This class is a container for operations, every public method in this class will be taken as an extension operation.
 */
public class JpCharactersConverterOperations {


    @DisplayName("Hankaku -> Zenkaku")
    @MediaType(value = ANY, strict = false)
    public String convertHankakuToZenkaku(String input_text) {
	int op_flags = 0;
	op_flags |= KanaConverter.OP_HAN_KATA_TO_ZEN_KATA;
	op_flags |= KanaConverter.OP_HAN_ASCII_TO_ZEN_ASCII;
	op_flags |= KanaConverter.OP_HAN_LETTER_TO_ZEN_LETTER;
	op_flags |= KanaConverter.OP_HAN_NUMBER_TO_ZEN_NUMBER;
	op_flags |= KanaConverter.OP_HAN_SPACE_TO_ZEN_SPACE;

	return KanaConverter.convertKana(input_text, op_flags);
    }
    
}
