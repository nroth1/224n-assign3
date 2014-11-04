package cs224n.corefsystems;

import java.util.*;

import cs224n.coref.*;
import cs224n.util.Pair;
import cs224n.coref.Sentence.Token;

public class RuleBased implements CoreferenceSystem {


  private HashMap<String,Set<String>> coOccuringMentions = new HashMap<String,Set<String>>();

  private void addString(String s1,String s2){
    if(!coOccuringMentions.containsKey(s1)){
      coOccuringMentions.put(s1, new HashSet<String>());
    }
    Set<String> cos = coOccuringMentions.get(s1);
    cos.add(s2);
  }

  private void generateAndMarkPairs(Entity e){
    Set<Mention> mentions = e.mentions;
    for(Mention m1 : mentions){
      for(Mention m2 : mentions){
        String headWord1 = m1.headWord().toLowerCase();
        String headWord2 = m2.headWord().toLowerCase();
        addString(headWord1,headWord2);
        addString(headWord2,headWord1);
      }
    }
  }

  @Override
  public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
    //For each entity, for every pair of metions in entity, note that pair occured together.
    for(Pair<Document, List<Entity>> datum : trainingData){
      for(Entity e : datum.getSecond()){
        generateAndMarkPairs(e);
      }
    }
  }

  @Override
  public List<ClusteredMention> runCoreference(Document doc) {

    List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();

    // Exact match pass
    exactMatch(doc, mentions);

    // Appositive, predicate nominative, roles, etc.
    appositives(doc, mentions);

    // Head pass


    //(return the mentions)
    return mentions;

  }

  private void appositives(Document doc, List<ClusteredMention> mentions) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      Sentence sentence = m1.sentence;
      List<Token> tokens = sentence.tokens;
      for (Mention m2 : allMentions) {

        // "commas" and "is" as separators of PRP and NNP
        if (m2.sentence.equals(sentence)) {
          if (m2.beginIndexInclusive == m1.endIndexExclusive + 1 && (sentence.tokens.get(m1.endIndexExclusive).word().equals(",")) {
            if (tokens.get(m1.headWordIndex).posTag().equals("PRP") && tokens.get(m2.headWordIndex).posTag().equals("NNP")) {
//              System.out.println(m1);
//              System.out.println(m2);
//              System.out.println(sentence);
//              System.out.println("--------");

            }
          }
          if (sentence.tokens.get(m1.endIndexExclusive).word().equals("is")) {
            if () {

            }
//            System.out.println(m1.gloss());
//            System.out.println(m2.gloss());
//            System.out.println(tokens.get(m1.headWordIndex).posTag());
//            System.out.println(tokens.get(m2.headWordIndex).posTag());
//            System.out.println(m1.headWord());
//            System.out.println(m2.headWord());
//            System.out.println(sentence);
//            System.out.println("--------");

          }
        }
      }
    }


//    for (Sentence s : doc.sentences) {
//      List<Token> tokens = s.tokens;
//
//      // commas
//      for (int index = 2; index < s.length(); index++) {
//        if (tokens.get(index).isNoun()) {
//          if (tokens.get(index - 2).isNoun() && tokens.get(index - 1).word().equals(",")) {
//            System.out.println(tokens.get(index).word());
//            System.out.println(s.gloss());
//            System.out.println("-----");
//          }
//        }
//      }
//    }
  }


  /**
   * Exact matching
   * @param doc
   * @param mentions
   */
  private void exactMatch(Document doc, List<ClusteredMention> mentions) {
    Map<String, Entity> clusters = new HashMap<String, Entity>();
    //(for each mention...)
    for(Mention m : doc.getMentions()){
      //(...get its text)
      String mentionString = m.gloss();
      //(...if we've seen this text before...)
      if(clusters.containsKey(mentionString)){
        //(...add it to the cluster)
        mentions.add(m.markCoreferent(clusters.get(mentionString)));
      } else {
        //(...else create a new singleton cluster)
        ClusteredMention newCluster = m.markSingleton();
        mentions.add(newCluster);
        clusters.put(mentionString,newCluster.entity);
      }
    }
  }

}
