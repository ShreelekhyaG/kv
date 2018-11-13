package com.stackroute.knowledgevault.algos;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class FullTextSearchImpl implements FullTextSearch {

    private static String filesPath;
    private static String indexPath;

    public static final Logger LOGGER = LoggerFactory.getLogger(FullTextSearchImpl.class);

    @Override
    public String getIndexPath() {
        return indexPath;
    }

    @Override
    public String getFilesPath() {
        return filesPath;
    }

    @Override
    public void setIndexPath(String path) {
        indexPath = path;
    }

    @Override
    public void setFilesPath(String path) {
        filesPath = path;
    }

    /**
     *  This function indexes documents/source repositories and storing information in an inverted-index
     *  to facilitate fast search by using Lucene Library
     */
    @Override
    public String indexer() {
        LOGGER.info("creating indices....");
        Analyzer analyzer = new StandardAnalyzer();
        try {
            FSDirectory dir = new SimpleFSDirectory(new File(indexPath));
            if(Files.exists(Paths.get(indexPath))) {
                LOGGER.info("already indexed..");
                return "already indexed...";
            }
            IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_CURRENT,analyzer);
            IndexWriter indexWriter = new IndexWriter(dir,config);
            File repo = new File(filesPath);

            File[] resources = repo.listFiles();
            int id=0;
            for(File f: resources) {
                LOGGER.info("indexing file {}",f.getCanonicalPath());
                Document doc = new Document();
                doc.add(new Field("path",f.getPath(), Field.Store.YES, Field.Index.ANALYZED));
                doc.add(new Field("id",String.valueOf(id), Field.Store.YES, Field.Index.ANALYZED));
                doc.add(new Field("name",f.getName(), Field.Store.YES, Field.Index.ANALYZED));
                id++;
                Reader reader = new FileReader(f.getCanonicalPath());
                doc.add(new Field("contents",reader,Field.TermVector.WITH_POSITIONS_OFFSETS));
                indexWriter.addDocument(doc);
                reader.close();
            }
            indexWriter.close();

            LOGGER.info("indexing complete....");
        }
        catch(Exception e) {
            LOGGER.error(String.valueOf(e.getMessage()));
            return "failure";
        }
        return "success";
    }

    /**
     * This function uses Lucene Library to  provide fast search of a given word in a  huge corpus of documents
     * @param data : the keyword that needs to be searched in the medical dictionaries/ repositories
     * @return: for now,it's just the location of all documents that contain the keyword.
     */

    @Override
    public List<String> search(String data) {
        StringBuilder sb = new StringBuilder();
        LOGGER.info("searching the keyword: {}",data);
        List<String> spanArray = new ArrayList<>();
        try {
            IndexReader iReader = IndexReader.open(FSDirectory.open(new File(indexPath)));
            IndexSearcher searcher = new IndexSearcher(iReader);

            SpanQuery query = new SpanTermQuery(new Term("contents", data));
            TopDocs results = searcher.search(query,10);
            Map<Term, TermContext> termContexts = new HashMap<>();

            for (int i = 0; i < results.scoreDocs.length; i++) {
                ScoreDoc scoreDoc = results.scoreDocs[i];
                LOGGER.info("Score of the keyword: {} in {} is = {} ",data,searcher.doc(i).get("name"),scoreDoc.score);
            }

            for (AtomicReaderContext atomic : iReader.leaves()) {
                Bits bitset = atomic.reader().getLiveDocs();
                Spans spans = query.getSpans(atomic, bitset, termContexts);
                while (spans.next()) {
                    int docid = atomic.docBase + spans.doc();
                    spanArray.add("Doc with name: " + searcher.doc(docid).get("name") +" and location is " + spans.end()+"th word in the document\n");
                };
            };
            for(String s: spanArray) LOGGER.info(s);
        }
        catch(Exception e) {
            LOGGER.debug(e.getMessage());
            return Collections.singletonList("something went wrong..");
        }

        if(spanArray.isEmpty()) return Collections.singletonList("not found");
        spanArray.add("found");
        return spanArray;
    }

    @Override
    public List<String> getRelevantTerms(String path) {
        indexer();
        LOGGER.info("please wait while we do the muscle-work.....");
        Double[][] matrix;
        TreeMap<Double,String> scoreList = new TreeMap<>(Collections.reverseOrder());
        try {
            File file = new File(path);
            String[] text = FileUtils.readFileToString(file,"UTF-8").trim().split("\\s+");
            File[] corpus = new File(getFilesPath()).listFiles();
            matrix = new Double[text.length][corpus.length];
            for(int i=0;i<matrix.length;i++) {
                for(int j=0;j<matrix[i].length;j++) matrix[i][j] = Double.valueOf(0);
            }
            for(int i=0;i<text.length;i++) {
                IndexReader iReader = IndexReader.open(FSDirectory.open(new File(getIndexPath())));
                IndexSearcher searcher = new IndexSearcher(iReader);
                SpanQuery query = new SpanTermQuery(new Term("contents",text[i]));
                TopDocs results = searcher.search(query,10);
                for(int j=0;j<results.totalHits;j++) {
                    String docid = searcher.doc(j).get("id");
                    scoreList.put((double) results.getMaxScore(),text[i]);
                    matrix[i][Integer.parseInt(docid)] = Double.valueOf(results.scoreDocs[j].score);
                }
            }
        }
        catch (Exception e) {
            LOGGER.debug(e.getMessage());
            return null;
        }
//        for(int i=0;i<matrix.length;i++) {
//            for(int j=0;j<matrix[i].length;j++) LOGGER.info("score in Document{} is : {}",j+1,matrix[i][j]);
//        }

        List<String> keywords = new ArrayList<>();
        int cnt=0;
        for (Map.Entry<Double,String> entry : scoreList.entrySet()) {
//            LOGGER.info("Score = {}, Value = {}", entry.getKey(), entry.getValue());
            if (cnt < 20) {
                keywords.add(entry.getValue());
                cnt++;
            }
        }
        String res = keywords.toString();
        LOGGER.info(res);
        return keywords;
    }
}
