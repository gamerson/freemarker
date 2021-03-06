/*
 * Copyright (c) 2003 The Visigoth Software Society. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowledgement:
 *       "This product includes software developed by the
 *        Visigoth Software Society (http://www.visigoths.org/)."
 *    Alternately, this acknowledgement may appear in the software itself,
 *    if and wherever such third-party acknowledgements normally appear.
 *
 * 4. Neither the name "FreeMarker", "Visigoth", nor any of the names of the
 *    project contributors may be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact visigoths@visigoths.org.
 *
 * 5. Products derived from this software may not be called "FreeMarker" or "Visigoth"
 *    nor may "FreeMarker" or "Visigoth" appear in their names
 *    without prior written permission of the Visigoth Software Society.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE VISIGOTH SOFTWARE SOCIETY OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Visigoth Software Society. For more
 * information on the Visigoth Software Society, please see
 * http://www.visigoths.org/
 */

package freemarker.core;

import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import freemarker.ext.beans.CollectionModel;
import freemarker.template.SimpleNumber;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateCollectionModel;
import freemarker.template.TemplateDateModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateModelIterator;
import freemarker.template.TemplateModelListSequence;
import freemarker.template.TemplateNumberModel;
import freemarker.template.TemplateScalarModel;
import freemarker.template.TemplateSequenceModel;
import freemarker.template.utility.Constants;
import freemarker.template.utility.StringUtil;

/**
 * A holder for builtins that operate exclusively on sequence or collection left-hand value.
 */
class SequenceBuiltins {
    
    // Can't be instantiated
    private SequenceBuiltins() { }
    
    private abstract static class SequenceBuiltIn extends BuiltIn {
        TemplateModel _eval(Environment env)
                throws TemplateException
        {
            TemplateModel model = target.eval(env);
            if (!(model instanceof TemplateSequenceModel)) {
                throw new UnexpectedTypeException(target, model, "sequence", env);
            }
            return calculateResult((TemplateSequenceModel) model);
        }
        abstract TemplateModel calculateResult(TemplateSequenceModel tsm)
        throws
            TemplateModelException;
    }

    static class firstBI extends SequenceBuiltIn {
        TemplateModel calculateResult(TemplateSequenceModel tsm)
        throws
            TemplateModelException
        {
            if (tsm.size() == 0) {
                return null;
            }
            return tsm.get(0);
        }
    }

    static class lastBI extends SequenceBuiltIn {
        TemplateModel calculateResult(TemplateSequenceModel tsm)
        throws
            TemplateModelException
        {
            if (tsm.size() == 0) {
                return null;
            }
            return tsm.get(tsm.size() -1);
        }
    }

    static class reverseBI extends SequenceBuiltIn {
        TemplateModel calculateResult(TemplateSequenceModel tsm) {
            if (tsm instanceof ReverseSequence) {
                return ((ReverseSequence) tsm).seq;
            } else {
                return new ReverseSequence(tsm);
            }
        }

        private static class ReverseSequence implements TemplateSequenceModel
        {
            private final TemplateSequenceModel seq;

            ReverseSequence(TemplateSequenceModel seq)
            {
                this.seq = seq;
            }

            public int size() throws TemplateModelException
            {
                return seq.size();
            }

            public TemplateModel get(int index) throws TemplateModelException
            {
                return seq.get(seq.size() - 1 - index);
            }
        }
    }

    static class sortBI extends SequenceBuiltIn {
        
        static final int KEY_TYPE_NOT_YET_DETECTED = 0;
        static final int KEY_TYPE_STRING = 1;
        static final int KEY_TYPE_NUMBER = 2;
        static final int KEY_TYPE_DATE = 3;
        static final int KEY_TYPE_BOOLEAN = 4;
        
        TemplateModel calculateResult(TemplateSequenceModel seq)
                throws TemplateModelException {
            return sort(seq, null);
        }

        static Object[] startErrorMessage(int keyNamesLn) {
            return new Object[] { (keyNamesLn == 0 ? "?sort" : "?sort_by(...)"), " failed: " };
        }

        static Object[] startErrorMessage(int keyNamesLn, int index) {
            return new Object[] {
                    (keyNamesLn == 0 ? "?sort" : "?sort_by(...)"),
                    " failed at sequence index ", new Integer(index),
                    (index == 0 ? ": " : " (0-based): ") };
        }
        
        static TemplateModelException newInconsistentSortKeyTypeException(
                int keyNamesLn, String firstType, String firstTypePlural, int index, TemplateModel key) {
            String valueInMsg;
            String valuesInMsg;
            if (keyNamesLn == 0) {
                valueInMsg  = "value";
                valuesInMsg  = "values";
            } else {
                valueInMsg  = "key value";
                valuesInMsg  = "key values";
            }
            return new _TemplateModelException(new Object[] {
                    startErrorMessage(keyNamesLn, index),
                    "All ", valuesInMsg, " in the sequence must be ",
                    firstTypePlural, ", because the first ", valueInMsg,
                    " was that. However, the ", valueInMsg,
                    " of the current item isn't a ", firstType, " but a ",
                    new _DelayedFTLTypeDescription(key), "."});
        }
        
        /**
         * Sorts a sequence for the <tt>sort</tt> and <tt>sort_by</tt>
         * built-ins.
         * 
         * @param seq the sequence to sort.
         * @param keyNames the name of the subvariable whose value is used for the
         *     sorting. If the sorting is done by a sub-subvaruable, then this
         *     will be of length 2, and so on. If the sorting is done by the
         *     sequene items directly, then this argument has to be 0 length
         *     array or <code>null</code>.
         * @return a new sorted sequence, or the original sequence if the
         *     sequence length was 0.
         */
        static TemplateSequenceModel sort(TemplateSequenceModel seq, String[] keyNames)
                throws TemplateModelException {
            int ln = seq.size();
            if (ln == 0) return seq;
            
            ArrayList res = new ArrayList(ln);

            int keyNamesLn = keyNames == null ? 0 : keyNames.length;

            // Copy the Seq into a Java List[KVP] (also detects key type at the 1st item):
            int keyType = KEY_TYPE_NOT_YET_DETECTED;
            Comparator keyComparator = null;
            for (int i = 0; i < ln; i++) {
                final TemplateModel item = seq.get(i);
                TemplateModel key = item;
                for (int keyNameI = 0; keyNameI < keyNamesLn; keyNameI++) {
                    try {
                        key = ((TemplateHashModel) key).get(keyNames[keyNameI]);
                    } catch (ClassCastException e) {
                        if (!(key instanceof TemplateHashModel)) {
                            throw new _TemplateModelException(new Object[] {
                                    startErrorMessage(keyNamesLn, i),
                                    (keyNameI == 0
                                            ? "Sequence items must be hashes when using ?sort_by. "
                                            : "The " + StringUtil.jQuote(keyNames[keyNameI - 1])),
                                    " subvariable is not a hash, so ?sort_by ",
                                    "can't proceed with getting the ",
                                    new _DelayedJQuote(keyNames[keyNameI]),
                                    " subvariable." });
                        } else {
                            throw e;
                        }
                    }
                    if (key == null) {
                        throw new _TemplateModelException(new Object[] {
                                startErrorMessage(keyNamesLn, i),
                                "The " + StringUtil.jQuote(keyNames[keyNameI]), " subvariable was not found." });
                    }
                } // for each key
                
                if (keyType == KEY_TYPE_NOT_YET_DETECTED) {
                    if (key instanceof TemplateScalarModel) {
                        keyType = KEY_TYPE_STRING;
                        keyComparator = new LexicalKVPComparator(
                                Environment.getCurrentEnvironment().getCollator());
                    } else if (key instanceof TemplateNumberModel) {
                        keyType = KEY_TYPE_NUMBER;
                        keyComparator = new NumericalKVPComparator(
                                Environment.getCurrentEnvironment()
                                        .getArithmeticEngine());
                    } else if (key instanceof TemplateDateModel) {
                        keyType = KEY_TYPE_DATE;
                        keyComparator = new DateKVPComparator();
                    } else if (key instanceof TemplateBooleanModel) {
                        keyType = KEY_TYPE_BOOLEAN;
                        keyComparator = new BooleanKVPComparator();
                    } else {
                        throw new _TemplateModelException(new Object[] {
                                startErrorMessage(keyNamesLn, i),
                                "Values used for sorting must be numbers, strings, date/times or booleans." });
                    }
                }
                switch(keyType) {
                    case KEY_TYPE_STRING:
                        try {
                            res.add(new KVP(
                                    ((TemplateScalarModel) key).getAsString(),
                                    item));
                        } catch (ClassCastException e) {
                            if (!(key instanceof TemplateScalarModel)) {
                                throw newInconsistentSortKeyTypeException(
                                        keyNamesLn, "string", "strings", i, key);
                            } else {
                                throw e;
                            }
                        }
                        break;
                        
                    case KEY_TYPE_NUMBER:
                        try {
                            res.add(new KVP(
                                    ((TemplateNumberModel) key).getAsNumber(),
                                    item));
                        } catch (ClassCastException e) {
                            if (!(key instanceof TemplateNumberModel)) {
                                throw newInconsistentSortKeyTypeException(
                                        keyNamesLn, "number", "numbers", i, key);
                            }
                        }
                        break;
                        
                    case KEY_TYPE_DATE:
                        try {
                            res.add(new KVP(
                                    ((TemplateDateModel) key).getAsDate(),
                                    item));
                        } catch (ClassCastException e) {
                            if (!(key instanceof TemplateDateModel)) {
                                throw newInconsistentSortKeyTypeException(
                                        keyNamesLn, "date/time", "date/times", i, key);
                            }
                        }
                        break;
                        
                    case KEY_TYPE_BOOLEAN:
                        try {
                            res.add(new KVP(
                                    ((TemplateBooleanModel) key).getAsBoolean() ?
                                            Boolean.TRUE : Boolean.FALSE,
                                    item));
                        } catch (ClassCastException e) {
                            if (!(key instanceof TemplateBooleanModel)) {
                                throw newInconsistentSortKeyTypeException(
                                        keyNamesLn, "boolean", "booleans", i, key);
                            }
                        }
                        break;
                        
                    default:
                        throw new RuntimeException("FreeMarker bug: Unexpected key type");
                }
            }

            // Sort tje List[KVP]:
            try {
                Collections.sort(res, keyComparator);
            } catch (Exception exc) {
                throw new _TemplateModelException(exc, new Object[] {
                        startErrorMessage(keyNamesLn), "Unexpected error while sorting:" + exc });
            }

            // Convert the List[KVP] to List[V]:
            for (int i = 0; i < ln; i++) {
                res.set(i, ((KVP) res.get(i)).value);
            }

            return new TemplateModelListSequence(res);
        }

        /**
         * Stores a key-value pair.
         */
        private static class KVP {
            private KVP(Object key, Object value) {
                this.key = key;
                this.value = value;
            }

            private Object key;
            private Object value;
        }

        private static class NumericalKVPComparator implements Comparator {
            private ArithmeticEngine ae;

            private NumericalKVPComparator(ArithmeticEngine ae) {
                this.ae = ae;
            }

            public int compare(Object arg0, Object arg1) {
                try {
                    return ae.compareNumbers(
                            (Number) ((KVP) arg0).key,
                            (Number) ((KVP) arg1).key);
                } catch (TemplateException e) {
                    throw new ClassCastException(
                        "Failed to compare numbers: " + e);
                }
            }
        }

        private static class LexicalKVPComparator implements Comparator {
            private Collator collator;

            LexicalKVPComparator(Collator collator) {
                this.collator = collator;
            }

            public int compare(Object arg0, Object arg1) {
                return collator.compare(
                        ((KVP) arg0).key, ((KVP) arg1).key);
            }
        }
        
        private static class DateKVPComparator implements Comparator, Serializable {

            public int compare(Object arg0, Object arg1) {
                return ((Date) ((KVP) arg0).key).compareTo(
                        (Date) ((KVP) arg1).key);
            }
        }
        
        private static class BooleanKVPComparator implements Comparator, Serializable {

            public int compare(Object arg0, Object arg1) {
                // JDK 1.2 doesn't have Boolean.compareTo
                boolean b0 = ((Boolean) ((KVP) arg0).key).booleanValue();
                boolean b1 = ((Boolean) ((KVP) arg1).key).booleanValue();
                if (b0) {
                    return b1 ? 0 : 1;
                } else {
                    return b1 ? -1 : 0;
                }
            }
        }
        
    }
    
    static class sort_byBI extends sortBI {
        TemplateModel calculateResult(TemplateSequenceModel seq) {
            return new BIMethod(seq);
        }
        
        class BIMethod implements TemplateMethodModelEx {
            TemplateSequenceModel seq;
            
            BIMethod(TemplateSequenceModel seq) {
                this.seq = seq;
            }
            
            public Object exec(List args)
                    throws TemplateModelException {
                // Should be:
                // checkMethodArgCount(args, 1);
                // But for BC:
                if (args.size() < 1) throw MessageUtil.newArgCntError("?" + key, args.size(), 1);
                
                String[] subvars;
                Object obj = args.get(0);
                if (obj instanceof TemplateScalarModel) {
                    subvars = new String[]{((TemplateScalarModel) obj).getAsString()};
                } else if (obj instanceof TemplateSequenceModel) {
                    TemplateSequenceModel seq = (TemplateSequenceModel) obj;
                    int ln = seq.size();
                    subvars = new String[ln];
                    for (int i = 0; i < ln; i++) {
                        Object item = seq.get(i);
                        try {
                            subvars[i] = ((TemplateScalarModel) item)
                                    .getAsString();
                        } catch (ClassCastException e) {
                            if (!(item instanceof TemplateScalarModel)) {
                                throw new _TemplateModelException(new Object[] {
                                        "The argument to ?", key, "(key), when it's a sequence, must be a "
                                        + "sequence of strings, but the item at index ", new Integer(i),
                                        " is not a string."});
                            }
                        }
                    }
                } else {
                    throw new _TemplateModelException(new Object[] {
                            "The argument to ?", key, "(key) must be a string (the name of the subvariable), or a "
                            + "sequence of strings (the \"path\" to the subvariable)." });
                }
                return sort(seq, subvars); 
            }
        }
    }

    private static boolean isBuggySeqButGoodCollection(
            TemplateModel model) {
        return model instanceof CollectionModel
                ? !((CollectionModel) model).getSupportsIndexedAccess()
                : false;
    }
    
    static class seq_containsBI extends BuiltIn {
        TemplateModel _eval(Environment env)
                throws TemplateException {
            TemplateModel model = target.eval(env);
            // In 2.3.x only, we prefer TemplateSequenceModel for
            // backward compatibility. In 2.4.x, we prefer TemplateCollectionModel. 
            if (model instanceof TemplateSequenceModel && !isBuggySeqButGoodCollection(model)) {
                return new BIMethodForSequence((TemplateSequenceModel) model, env);
            } else if (model instanceof TemplateCollectionModel) {
                return new BIMethodForCollection((TemplateCollectionModel) model, env);
            } else {
                throw new UnexpectedTypeException(target, model, "sequence or collection", env);
            }
        }

        private class BIMethodForSequence implements TemplateMethodModelEx {
            private TemplateSequenceModel m_seq;
            private Environment m_env;

            private BIMethodForSequence(TemplateSequenceModel seq, Environment env) {
                m_seq = seq;
                m_env = env;
            }

            public Object exec(List args)
                    throws TemplateModelException {
                checkMethodArgCount(args, 1);
                TemplateModel arg = (TemplateModel) args.get(0);
                int size = m_seq.size();
                for (int i = 0; i < size; i++) {
                    if (modelsEqual(i, m_seq.get(i), arg, m_env))
                        return TemplateBooleanModel.TRUE;
                }
                return TemplateBooleanModel.FALSE;
            }

        }
    
        private class BIMethodForCollection implements TemplateMethodModelEx {
            private TemplateCollectionModel m_coll;
            private Environment m_env;

            private BIMethodForCollection(TemplateCollectionModel coll, Environment env) {
                m_coll = coll;
                m_env = env;
            }

            public Object exec(List args)
                    throws TemplateModelException {
                checkMethodArgCount(args, 1);
                TemplateModel arg = (TemplateModel) args.get(0);
                TemplateModelIterator it = m_coll.iterator();
                int idx = 0;
                while (it.hasNext()) {
                    if (modelsEqual(idx, it.next(), arg, m_env))
                        return TemplateBooleanModel.TRUE;
                    idx++;
                }
                return TemplateBooleanModel.FALSE;
            }

        }
    
    }

    static class seq_index_ofBI extends BuiltIn {
        
        private int m_dir;

        public seq_index_ofBI(int dir) {
            m_dir = dir;
        }

        TemplateModel _eval(Environment env)
                throws TemplateException {
            return new BIMethod(env);
        }

        private class BIMethod implements TemplateMethodModelEx {
            
            protected final TemplateSequenceModel m_seq;
            protected final TemplateCollectionModel m_col;
            protected final Environment m_env;

            private BIMethod(Environment env)
                    throws TemplateException {
                TemplateModel model = target.eval(env);
                m_seq = model instanceof TemplateSequenceModel
                            && !isBuggySeqButGoodCollection(model)
                        ? (TemplateSequenceModel) model
                        : null;
                // In 2.3.x only, we deny the possibility of collection
                // access if there's sequence access. This is so to minimize
                // the change of compatibility issues; without this, objects
                // that implement both the sequence and collection interfaces
                // would suddenly start using the collection interface, and if
                // that's buggy that would surface now, breaking the application
                // that despite its bugs has worked earlier.
                m_col = m_seq == null && model instanceof TemplateCollectionModel
                        ? (TemplateCollectionModel) model
                        : null;
                if (m_seq == null && m_col == null) {
                    throw new UnexpectedTypeException(target, model, "sequence or collection", env);
                }
                
                m_env = env;
            }

            public final Object exec(List args)
                    throws TemplateModelException {
                int argCnt = args.size();
                checkMethodArgCount(argCnt, 1, 2);
                
                TemplateModel target = (TemplateModel) args.get(0);
                int foundAtIdx;
                if (argCnt > 1) {
                    int startIndex = getNumberMethodArg(args, 1).intValue();
                    // In 2.3.x only, we prefer TemplateSequenceModel for
                    // backward compatibility:
                    foundAtIdx = m_seq != null
                            ? findInSeq(target, startIndex)
                            : findInCol(target, startIndex);
                } else {
                    // In 2.3.x only, we prefer TemplateSequenceModel for
                    // backward compatibility:
                    foundAtIdx = m_seq != null
                            ? findInSeq(target)
                            : findInCol(target);
                }
                return foundAtIdx == -1 ? Constants.MINUS_ONE : new SimpleNumber(foundAtIdx);
            }
            
            public int findInSeq(TemplateModel target)
            throws TemplateModelException {
                final int seqSize = m_seq.size();
                final int actualStartIndex;
                
                if (m_dir == 1) {
                    actualStartIndex = 0;
                } else {
                    actualStartIndex = seqSize - 1;
                }
            
                return findInSeq(target, actualStartIndex, seqSize); 
            }
            
            private int findInSeq(TemplateModel target, int startIndex)
                    throws TemplateModelException {
                int seqSize = m_seq.size();
                
                if (m_dir == 1) {
                    if (startIndex >= seqSize) {
                        return -1;
                    }
                    if (startIndex < 0) {
                        startIndex = 0;
                    }
                } else {
                    if (startIndex >= seqSize) {
                        startIndex = seqSize - 1;
                    }
                    if (startIndex < 0) {
                        return -1;
                    }
                }
                
                return findInSeq(target, startIndex, seqSize); 
            }
        
            private int findInSeq(
                    TemplateModel target, int scanStartIndex, int seqSize)
                    throws TemplateModelException {
                if (m_dir == 1) {
                    for (int i = scanStartIndex; i < seqSize; i++) {
                        if (modelsEqual(i, m_seq.get(i), target, m_env)) return i;
                    }
                } else {
                    for (int i = scanStartIndex; i >= 0; i--) {
                        if (modelsEqual(i, m_seq.get(i), target, m_env)) return i;
                    }
                }
                return -1;
            }

            public int findInCol(TemplateModel target) throws TemplateModelException {
                return findInCol(target, 0, Integer.MAX_VALUE);
            }

            protected int findInCol(TemplateModel target, int startIndex)
                    throws TemplateModelException {
                if (m_dir == 1) {
                    return findInCol(target, startIndex, Integer.MAX_VALUE);
                } else {
                    return findInCol(target, 0, startIndex);
                }
            }
            
            protected int findInCol(TemplateModel target,
                    final int allowedRangeStart, final int allowedRangeEnd)
                    throws TemplateModelException {
                if (allowedRangeEnd < 0) return -1;
                
                TemplateModelIterator it = m_col.iterator();
                
                int foundAtIdx = -1;  // -1 is the return value for "not found"
                int idx = 0; 
                searchItem: while (it.hasNext()) {
                    if (idx > allowedRangeEnd) break searchItem;
                    
                    TemplateModel current = it.next();
                    if (idx >= allowedRangeStart) {
                        if (modelsEqual(idx, current, target, m_env)) {
                            foundAtIdx = idx;
                            if (m_dir == 1) break searchItem; // "find first"
                            // Otherwise it's "find last".
                        }
                    }
                    idx++;
                }
                return foundAtIdx;
            }
            
        }
    }

    static class chunkBI extends SequenceBuiltIn {

        TemplateModel calculateResult(TemplateSequenceModel tsm) throws TemplateModelException {
            return new BIMethod(tsm);
        }

        private class BIMethod implements TemplateMethodModelEx {
            
            private final TemplateSequenceModel tsm;

            private BIMethod(TemplateSequenceModel tsm) {
                this.tsm = tsm;
            }

            public Object exec(List args) throws TemplateModelException {
                checkMethodArgCount(args, 1, 2);
                int chunkSize = getNumberMethodArg(args, 0).intValue();
                
                return new ChunkedSequence(
                        tsm,
                        chunkSize,
                        args.size() > 1 ? (TemplateModel) args.get(1) : null);
            }
        }
        
        private static class ChunkedSequence implements TemplateSequenceModel {
            
            private final TemplateSequenceModel wrappedTsm;
            
            private final int chunkSize;
            
            private final TemplateModel fillerItem;
            
            private final int numberOfChunks;
            
            private ChunkedSequence(
                    TemplateSequenceModel wrappedTsm, int chunkSize, TemplateModel fillerItem)
                    throws TemplateModelException {
                if (chunkSize < 1) {
                    throw new _TemplateModelException(new Object[] {
                            "The 1st argument to ?', key, ' (...) must be at least 1." });
                }
                this.wrappedTsm = wrappedTsm;
                this.chunkSize = chunkSize;
                this.fillerItem = fillerItem;
                numberOfChunks = (wrappedTsm.size() + chunkSize - 1) / chunkSize; 
            }

            public TemplateModel get(final int chunkIndex)
                    throws TemplateModelException {
                if (chunkIndex >= numberOfChunks) {
                    return null;
                }
                
                return new TemplateSequenceModel() {
                    
                    private final int baseIndex = chunkIndex * chunkSize;

                    public TemplateModel get(int relIndex)
                            throws TemplateModelException {
                        int absIndex = baseIndex + relIndex;
                        if (absIndex < wrappedTsm.size()) {
                            return wrappedTsm.get(absIndex);
                        } else {
                            return absIndex < numberOfChunks * chunkSize
                                ? fillerItem
                                : null;
                        }
                    }

                    public int size() throws TemplateModelException {
                        return fillerItem != null || chunkIndex + 1 < numberOfChunks
                                ? chunkSize
                                : wrappedTsm.size() - baseIndex;
                    }
                    
                };
            }

            public int size() throws TemplateModelException {
                return numberOfChunks;
            }
            
        }
        
    }
    
    public static boolean modelsEqual(
            int seqItemIndex, TemplateModel seqItem, TemplateModel searchedItem,
            Environment env)
            throws TemplateModelException {
        try {
            return EvalUtil.compare(
                    seqItem, null,
                    EvalUtil.CMP_OP_EQUALS, null,
                    searchedItem, null,
                    null,
                    true, true, true, // The last one is true to emulate an old bug for BC 
                    env);
        } catch (TemplateException ex) {
            throw new _TemplateModelException(ex, new Object[] {
                    "This error has occured when comparing sequence item at 0-based index ", new Integer(seqItemIndex),
                    " to the searched item:\n", new _DelayedGetMessage(ex) });
        }
    }
 
    static class joinBI extends BuiltIn {
        
        TemplateModel _eval(Environment env) throws TemplateException {
            TemplateModel model = target.eval(env);
            if (model instanceof TemplateCollectionModel) {
                return new BIMethodForCollection((TemplateCollectionModel) model);
            } else if (model instanceof TemplateSequenceModel && !isBuggySeqButGoodCollection(model)) {
                return new BIMethodForSequence((TemplateSequenceModel) model);
            } else {
                throw new UnexpectedTypeException(target, model, "sequence or collection", env);
            }
        }

        private abstract class BIMethod implements TemplateMethodModelEx {

            public Object exec(List args)
                    throws TemplateModelException {
                checkMethodArgCount(args, 1, 3);
                return execJoin(
                        getStringMethodArg(args, 0),
                        getOptStringMethodArg(args, 1),
                        getOptStringMethodArg(args, 2));
            }

            protected abstract Object execJoin(String separator, String whenEmpty, String afterLast) throws TemplateModelException;

        }
        
        private class BIMethodForSequence extends BIMethod {
            
            private final TemplateSequenceModel m_seq;

            private BIMethodForSequence(TemplateSequenceModel seq) {
                m_seq = seq;
            }

            protected Object execJoin(String separator, String whenEmpty, String afterLast) throws TemplateModelException {
                StringBuffer sb = new StringBuffer();
                int size = m_seq.size();
                boolean hadItem = false;
                for (int i = 0; i < size; i++) {
                    TemplateModel item = m_seq.get(i);
                    if (item != null) {
                        if (hadItem) {
                            sb.append(separator);
                        } else {
                            hadItem = true;
                        }
                        try {
                            sb.append(EvalUtil.coerceModelToString(item, null, null, null));
                        } catch (TemplateModelException e) {
                            throw e;
                        } catch (TemplateException e) {
                            throw new _TemplateModelException(e, new Object[] {
                                    "?", key, " failed at index ", new Integer(i), ":" });
                        }
                    }
                }
                if (hadItem) {
                    if (afterLast != null) sb.append(afterLast);
                } else {
                    if (whenEmpty != null) sb.append(whenEmpty);
                }
                return new SimpleScalar(sb.toString());
           }

        }
    
        private class BIMethodForCollection extends BIMethod {
            
            private TemplateCollectionModel m_coll;

            private BIMethodForCollection(TemplateCollectionModel coll) {
                m_coll = coll;
            }

            protected Object execJoin(String separator, String whenEmpty, String afterLast) {
                // TODO Auto-generated method stub
                return null;
            }

        }
    
    }
    
}