import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by ildar on 22.03.15.
 * vm option -Xmx6g
 */
public class Main {

    private static final String INPUT_FILE = "sub_retail.dat",//"retail.dat",
            OUTPUT_FREQ_FILE = "frequent_itemsets.dat",
            OUTPUT_RULE_FILE = "rules.dat";

    private static final String DELIMITER = ";";

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        int[][] transactions = transactionsToMatrix(readFile());
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        System.out.println("Time to read file: " + elapsedTime + "; Transaction count = " + transactions.length);
        //-------------------------------
        startTime = System.currentTimeMillis();
        FrequentItemAcc freqItemAcc = new FrequentItemAcc(transactions);
        stopTime = System.currentTimeMillis();
        elapsedTime = stopTime - startTime;
        System.out.println("Time Create 1-frequent set: " + elapsedTime);
        //-------------------------------
        startTime = System.currentTimeMillis();
        while (freqItemAcc.generateNewFreqItemsets()) { //start backup previous generations
            System.out.println("freq-" + FrequentItemAcc.SIZE + "is generated");
        }

        stopTime = System.currentTimeMillis();
        elapsedTime = stopTime - startTime;
        System.out.println("Time algorithm execution: " + elapsedTime);
//        writeFrequentItems(freqItemAcc);
        showResults(freqItemAcc);

    }

    static int[][] transactionsToMatrix(List<Transaction> transactionList) {
        int[][] transMatrix = new int[transactionList.size()][];
        int i = 0;
        for (Transaction transaction : transactionList) {
            transMatrix[i++] = transaction.items;
        }
        return transMatrix;
    }



    static void showResults(FrequentItemAcc frequentItemAcc) {
        for (VDF vdf : frequentItemAcc.getFrequentCollec()) {
            System.out.println("FreqItemSet: " + vdf.FREQ_SIZE);
            System.out.println("count = " + vdf.length);
            System.out.println("------------------------");
        }
    }

    static void writeFrequentItems(FrequentItemAcc frequentItemAcc) {
        try (FileWriter fileWriter = new FileWriter(OUTPUT_FREQ_FILE)) {
            fileWriter.write(frequentItemAcc.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<Transaction> readFile() {
        List<Transaction> transactions = new LinkedList<>();
        Transaction transaction;
        String[] numbers;
        int[] items;
        String row;
        try (BufferedReader bf = new BufferedReader(new FileReader(INPUT_FILE))) {
            while ((row = bf.readLine()) != null) {
                numbers = row.split("\\s");
                transaction = new Transaction();
                items = new int[numbers.length];
                for (int j = 0; j < items.length; j++) {
                    try {
                        transaction.addItem(Integer.parseInt(numbers[j]));
                    } catch (NumberFormatException nfe) {
                        nfe.printStackTrace();
                    }
                }
                transaction.commit();
                transactions.add(transaction);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return transactions;
    }

}


class FrequentItemAcc {

    public static int SIZE;
    private static final int MIN_SUP = 2;

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (VDF vdf : frequentCollec) {
            sb.append(vdf);
            sb.append("\n");
        }
        return sb.toString();
    }

    private final int[][] transactions;

    private List<VDF> frequentCollec;

    public List<VDF> getFrequentCollec() {
        return frequentCollec;
    }


    public FrequentItemAcc(int[][] transactions) {
        SIZE = 1;
        this.transactions = transactions;
        Map<Integer, Integer> itemOccurMap = new HashMap<>();
        elicitItemset(transactions, itemOccurMap);
        int frequentItemsInts[] = new int[15000], countFreq = 0;
        for (Map.Entry<Integer, Integer> item : itemOccurMap.entrySet()) {
            if (item.getValue() >= MIN_SUP) {
                frequentItemsInts[countFreq++] = item.getKey();
            }
        }
        frequentItemsInts = Arrays.copyOfRange(frequentItemsInts, 0, countFreq);
        Arrays.sort(frequentItemsInts);
        int occurCount[] = new int[frequentItemsInts.length],
                i = 0;
        for (int item : frequentItemsInts) {
            occurCount[i++] = itemOccurMap.get(item);
        }
        VDF vdfOneFreq = new VDF(frequentItemsInts, occurCount);
        frequentCollec = new LinkedList<>();
        frequentCollec.add(vdfOneFreq);
    }

    public boolean generateNewFreqItemsets() {
        SIZE++;
        VDF candidates = generateCandidates();
        System.out.println("Candidates for freq_SIZE = " + SIZE + " is generated");
        VDF freqItems = findFrequentItemSet(candidates);
        if (freqItems.isEmpty()) {
            return false;
        }
        frequentCollec.add(freqItems);
        return true;
    }

    public VDF generateCandidates() {
        VDF candidates = new VDF(SIZE, -1);
        VDF prevFreqItems = frequentCollec.get(SIZE - 2);
        int lengthPrevFreq = prevFreqItems.length;
        next:
        for (int rowIdA = 0; rowIdA < lengthPrevFreq; rowIdA++) {
            for (int rowIdB = rowIdA + 1; rowIdB < lengthPrevFreq; rowIdB++) {
                if (prevFreqItems.hasSamePrefixTo(rowIdA, rowIdB)) {
                    candidates.merge(prevFreqItems, rowIdA, rowIdB);
                } else {
                    continue next;
                }
            }
        }
        return candidates;

    }

    private VDF findFrequentItemSet(VDF vdfCandidates) {
        VDF freqItems = new VDF(SIZE, vdfCandidates.length);
        int pos_cand;
        for (int rowId = 0; rowId < vdfCandidates.length; rowId++) {
            for (int[] transaction : transactions) {
                pos_cand = 0;
                for (int item : transaction) {
                    if (item == vdfCandidates.getItem(pos_cand, rowId)) {
                        if (++pos_cand == vdfCandidates.FREQ_SIZE) {
                            vdfCandidates.incrementOccur(rowId);
                            break;
                        }
                    }
                }

            }
            if (vdfCandidates.getOccurCount(rowId) >= MIN_SUP) {
                freqItems.add(vdfCandidates, rowId);
            }
        }
        freqItems.commit();

        return freqItems;
    }


    private void elicitItemset(int[][] transactions, Map<Integer, Integer> itemOccurMap) {
        for (int[] transaction : transactions) {
            for (int item : transaction) {
                if (itemOccurMap.containsKey(item)) {
                    int count = itemOccurMap.get(item);
                    itemOccurMap.put(item, ++count);
                } else {
                    itemOccurMap.put(item, 1);
                }
            }
        }
    }

}

class VDF {

    private static final int VDF_SIZE_STEP = 101500000;
    public final int FREQ_SIZE;
    private int[][] verticalIndex;
    private int[] countOccur;
    public int length;

    public VDF(int size, int arrayLength) {
        FREQ_SIZE = size;
        arrayLength = (arrayLength == -1) ? VDF_SIZE_STEP : arrayLength;
        verticalIndex = new int[FREQ_SIZE][arrayLength]; // ToDo get appropriate initialisation
        countOccur = new int[arrayLength];
    }

    public VDF(int[] verticalIndexPart, int[] countOccur) {
        FREQ_SIZE = 1;
        verticalIndex = new int[1][];
        verticalIndex[0] = verticalIndexPart;
        this.countOccur = countOccur;
        length = verticalIndexPart.length;
    }

    public int getOccurCount(int rowId) {
        return countOccur[rowId];
    }

    public void add(VDF vdfFrom, int rowId) {
        for (int i = 0; i < FREQ_SIZE; i++) {
            verticalIndex[i][length] = vdfFrom.getItem(i, rowId);
        }
        countOccur[length] = vdfFrom.getOccurCount(rowId);
        length++;
    }

    public void incrementOccur(int rowId) {
        countOccur[rowId]++;
    }

    public boolean isEmpty() {
        return length == 0;
    }

    public boolean hasSamePrefixTo(int rowIdA, int rowIdB) {

        if (FREQ_SIZE > 1) {
            int i, prefixLength = FREQ_SIZE - 1;
            for (i = 0; i < prefixLength && verticalIndex[i][rowIdA] == verticalIndex[i][rowIdB]; i++) ;
            return i == prefixLength;
        }
        return true;
    }

    public void commit() {
        if (length == 0 || countOccur[length - 1] == 0) { //if there is nothing to eliminate we would not to do it
            return;
        }
        for (int i = 0; i < FREQ_SIZE; i++) {
            verticalIndex[i] = Arrays.copyOfRange(verticalIndex[i], 0, length);
        }
        countOccur = Arrays.copyOfRange(countOccur, 0, length);
    }

    public int getItem(int i, int rowId) {
        return verticalIndex[i][rowId];
    }

    private int getLstPart(int rowId) {
        return verticalIndex[FREQ_SIZE - 1][rowId];
    }

    /**
     * vdfFrom's FREQ_SIZE must be lower for 1
     * i < j
     */
    public void merge(VDF vdfFrom, int rowIdA, int rowIdB) {
        int prefixLength = FREQ_SIZE - 2, rowId = length;
        for (int i = 0; i < prefixLength; i++) {
            verticalIndex[i][length] = vdfFrom.getItem(i, rowIdA);
        }

        verticalIndex[FREQ_SIZE - 2][rowId] = vdfFrom.getLstPart(rowIdA);
        verticalIndex[FREQ_SIZE - 1][rowId] = vdfFrom.getLstPart(rowIdB);
        length++;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("VDF:");
        sb.append(FREQ_SIZE);
        sb.append("{");
        sb.append("length=");
        sb.append(length);
        sb.append(", ");
        int i = 0;
        for (int[] col : verticalIndex) {
            sb.append("Col-");
            sb.append(i++);
            sb.append("=");
            sb.append(Arrays.toString(col));
            sb.append(" ");
        }
        sb.append(", countOccur=");
        sb.append(Arrays.toString(countOccur));

        sb.append('}');

        return sb.toString();
    }


}

class Transaction {
    public int[] items;
    private List<Integer> internalItems;
    private static final int minArraySize = 30;

    public void addItem(int itemNum) {
        internalItems.add(itemNum);
    }

    public Transaction() {
        internalItems = new ArrayList<>(minArraySize);
    }

    @Override
    public String toString() {
        return Arrays.toString(items);
    }

    public void commit() {
        Collections.sort(internalItems);
        eliminateRepeatItem(internalItems);
        items = new int[internalItems.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = internalItems.get(i);   //Integer Object to primitive int: stupid Java
        }
        internalItems = null;
    }

    private static void eliminateRepeatItem(List<Integer> items) {
        if (items.size() > 1) {
            for (int i = 1, cur = 0; i < items.size(); i = cur + 1) {
                if (items.get(cur).equals(items.get(i))) {
                    items.remove(i);
                } else {
                    cur++;
                }
            }
        }
    }

}


