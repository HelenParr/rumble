package utils;

import java.util.Arrays;
import java.util.List;

public class SequenceStringComparator {

    public static boolean sequenceStringsAreEqual(String sequence1, String sequence2) {
        List<String> sequence1Items = Arrays.asList(sequence1.split(","));
        List<String> sequence2Items = Arrays.asList(sequence2.split(","));
        for (String sequence1Item : sequence1Items) {
            if (!sequence2Items.stream().anyMatch(s2 -> s2.trim().replace("(", "").replace(")", "")
                    .equals(sequence1Item.trim().replace("(", "").replace(")", ""))))
                return false;
        }
        return true;
    }
}
