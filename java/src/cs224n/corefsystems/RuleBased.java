package cs224n.corefsystems;

import java.util.*;

import cs224n.coref.*;
import cs224n.coref.Pronoun.Speaker;
import cs224n.util.Pair;
import cs224n.coref.Sentence.Token;

public class RuleBased implements CoreferenceSystem {

  // Map of co-occuring mentions in the training documents
  private HashMap<String, Set<String>> coOccuringMentions = new HashMap<String, Set<String>>();

  // Statistics computed from the document (token-wise distance between mentions of the same sentence)
  private int totalDistance = 0;
  private int n = 0;


  private void addString(String s1, String s2) {
    if (!coOccuringMentions.containsKey(s1)) {
      coOccuringMentions.put(s1, new HashSet<String>());
    }
    Set<String> cos = coOccuringMentions.get(s1);
    cos.add(s2);
  }


  private void generateAndMarkPairs(Entity e) {
    Set<Mention> mentions = e.mentions;
    for (Mention m1 : mentions) {
      for (Mention m2 : mentions) {
        String headWord1 = m1.headWord().toLowerCase();
        String headWord2 = m2.headWord().toLowerCase();
        addString(headWord1, headWord2);
        addString(headWord2, headWord1);
      }
    }
  }


  private void updateDistance(Entity e) {
    Set<Mention> allMentions = e.mentions;
    for (Mention m1 : allMentions) {
      Sentence sentence = m1.sentence;
      for (Mention m2 : allMentions) {
        if (m2.sentence.equals(sentence)) {
          totalDistance += Math.abs(m2.beginIndexInclusive - m1.endIndexExclusive);
          n++;
        }
      }
    }
  }

  @Override
  public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
    //For each entity, for every pair of mentions in entity, note that pair occurred together.
    for (Pair<Document, List<Entity>> datum : trainingData) {
      for (Entity e : datum.getSecond()) {
        generateAndMarkPairs(e);
        updateDistance(e);
      }
    }
  }


  public List<ClusteredMention> entitiesToClusters(ArrayList<Entity> entities) {
    List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
    for (Entity e : entities) {
      for (Mention m : e.mentions) {
        mentions.add(m.markCoreferent(e));
      }
    }
    return mentions;
  }

  @Override
  public List<ClusteredMention> runCoreference(Document doc) {

    Map<String, Entity> clusters = new HashMap<String, Entity>();
    ArrayList<Entity> entities = new ArrayList<Entity>();

    // Pass 1: Exact match
    exactMatch(doc, clusters, entities);

    // Pass 2: Precise constructs
    appositives(doc, clusters, entities);


    // Pass 2:
//    fuzzyMatch(doc, clusters, entities);

    // Appositive, predicate nominative, roles, etc.
//    appositives(doc, clusters, entities);
//    headWords(doc, clusters, entities);
//    System.out.println(entities);
//    headWordsRelaxed(doc, clusters, entities);
//    System.out.println(entities);

//    agreeWords(doc, clusters, entities);
//    pluralWords(doc, clusters, entities);
//    genderNameWords(doc, clusters, entities);
//    genderWords(doc, clusters, entities);
    pronounPass(doc, clusters, entities);

    //System.out.println(clusters);
    //System.out.println("=================");
    //System.out.println(entities);

    // Head pass

    //(return the mentions)

//    System.out.println("clusters" + entities);
//    System.out.println("distance" + totalDistance);
//    System.out.println("n" + n);

    return entitiesToClusters(entities);

  }

  private void headWordsRelaxed(Document doc, Map<String, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {

      for (Mention m2 : allMentions) {
        Sentence sentence = m2.sentence;
        List<Token> tokens = sentence.tokens;

        if (clusters.get(m1.gloss()).equals(clusters.get(m2.gloss()))) continue;
        int begin = m2.beginIndexInclusive;
        int end = m2.endIndexExclusive;
        for (int i = begin; i < end; i++) {
          if (m1.headWord().toLowerCase().equals(tokens.get(i).word().toLowerCase()) && !Pronoun.isSomePronoun(m1.headWord())) {
            System.out.println(m1.headWord());


            //System.out.println("clusters" + entities);
            mergeEntities(m1, m2, clusters, entities);
            break;
            //System.out.println("clusters" + entities);
          }
        }
      }
    }

  }


  private void pronounPass(Document doc, Map<String, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      Sentence sentence = m1.sentence;
      List<Token> tokens = sentence.tokens;
      for (Mention m2 : allMentions) {
        if (m2.sentence.equals(sentence)) {
          if (!(Math.abs(m1.beginIndexInclusive - m2.endIndexExclusive) <= (.5 * (float) totalDistance) / n)) continue;
          if (clusters.get(m1.gloss()).equals(clusters.get(m2.gloss()))) continue;
          Pronoun p1 = Pronoun.valueOrNull(m1.headWord());
          if (p1 != null && (m2.headToken().isNoun() || m2.headToken().isProperNoun() || m2.headToken().isPluralNoun())) {
            if (p1.plural && m2.headToken().isPluralNoun() && p1.speaker == Speaker.THIRD_PERSON) {
              mergeEntities(m1, m2, clusters, entities);
            } else if (!p1.plural && (!m2.headToken().isPluralNoun()) && p1.speaker == Speaker.THIRD_PERSON) {
              mergeEntities(m1, m2, clusters, entities);
            }
          }
        }
      }
    }
  }

  private void genderNameWords(Document doc, Map<String, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      Sentence sentence = m1.sentence;
      List<Token> tokens = sentence.tokens;
      for (Mention m2 : allMentions) {
        if (clusters.get(m1.gloss()).equals(clusters.get(m2.gloss()))) continue;
        String headWord1 = m1.headWord();
        String headWord2 = m2.headWord();
        Pronoun p2 = Pronoun.valueOrNull(m2.headWord());
        Pronoun p1 = Pronoun.valueOrNull(m1.headWord());
        // "commas" and "is" as separators of PRP and NNP
        if (m2.sentence.equals(sentence)) {
          if (Name.isName(headWord1) && p2 != null) {
            if ((Name.gender(headWord1) == p2.gender) && (p2.gender != Gender.EITHER)) {

              mergeEntities(m1, m2, clusters, entities);
            }
          }
        }
      }
    }
  }

  private void agreeWords(Document doc, Map<String, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      Sentence sentence = m1.sentence;
      List<Token> tokens = sentence.tokens;
      for (Mention m2 : allMentions) {
        if (clusters.get(m1.gloss()).equals(clusters.get(m2.gloss()))) continue;

        // "commas" and "is" as separators of PRP and NNP
        if (m2.sentence.equals(sentence)) {
          Pronoun p2 = Pronoun.valueOrNull(m2.headWord());
          Pronoun p1 = Pronoun.valueOrNull(m1.headWord());
          if (p1 != null && p2 != null) {
            if (p2.speaker.equals(p1.speaker)) {
              mergeEntities(m1, m2, clusters, entities);

              //System.out.println(tokens.get(m1.headWordIndex).posTag());
              //System.out.println(tokens.get(m2.headWordIndex).posTag());
            }
          }
        }
      }
    }

  }


  private void pluralWords(Document doc, Map<String, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      Sentence sentence = m1.sentence;
      List<Token> tokens = sentence.tokens;
      for (Mention m2 : allMentions) {
        if (clusters.get(m1.gloss()).equals(clusters.get(m2.gloss()))) continue;

        // "commas" and "is" as separators of PRP and NNP
        if (m2.sentence.equals(sentence)) {
          Pronoun p2 = Pronoun.valueOrNull(m2.headWord());
          Pronoun p1 = Pronoun.valueOrNull(m1.headWord());
          if (p1 != null && p2 != null) {
            if ((p2.plural == p1.plural)) {
              mergeEntities(m1, m2, clusters, entities);

              //System.out.println(tokens.get(m1.headWordIndex).posTag());
              //System.out.println(tokens.get(m2.headWordIndex).posTag());
            }
          }
        }
      }
    }

  }


  private void genderWords(Document doc, Map<String, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      Sentence sentence = m1.sentence;
      List<Token> tokens = sentence.tokens;
      for (Mention m2 : allMentions) {
        if (clusters.get(m1.gloss()).equals(clusters.get(m2.gloss()))) continue;

        // "commas" and "is" as separators of PRP and NNP
        if (m2.sentence.equals(sentence)) {
          Pronoun p2 = Pronoun.valueOrNull(m2.headWord());
          Pronoun p1 = Pronoun.valueOrNull(m1.headWord());
          if (p1 != null && p2 != null) {
            if (p2.gender.isCompatible(p1.gender)) {
              mergeEntities(m1, m2, clusters, entities);

              //System.out.println(tokens.get(m1.headWordIndex).posTag());
              //System.out.println(tokens.get(m2.headWordIndex).posTag());
            }
          }
        }
      }
    }

  }


  private void headWords(Document doc, Map<String, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      for (Mention m2 : allMentions) {
        if (clusters.get(m1.gloss()).equals(clusters.get(m2.gloss()))) continue;

        if (coOccuringMentions.containsKey(m1.headWord()) && coOccuringMentions.get(m1.headWord()).contains(m2.headWord())) {
          mergeEntities(m1, m2, clusters, entities);
        }

      }
    }
  }

  private Gender getGender(Entity e) {
    Gender g = Gender.EITHER;
    for (Mention m : e.mentions) {
      Pronoun p = Pronoun.valueOrNull(m.headWord());
      if (p != null && p.gender != null && p.gender != Gender.EITHER && p.gender != Gender.NEUTRAL) {
        g = p.gender;
      }
    }
    return g;
  }

  private boolean getPlural(Entity e) {
    for (Mention m : e.mentions) {
      Pronoun p = Pronoun.valueOrNull(m.headWord());
      if (p != null && p.plural) {
        return true;
      }
    }
    return false;
  }

  private Speaker getSpeaker(Entity e) {
    for (Mention m : e.mentions) {
      Pronoun p = Pronoun.valueOrNull(m.headWord());
      if (p != null && p.speaker != null) {
        return p.speaker;
      }
    }
    return null;
  }

  private void mergeEntities(Mention m1, Mention m2, Map<String, Entity> clusters, ArrayList<Entity> entities) {
    Entity e1 = clusters.get(m1.gloss());
    Entity e2 = clusters.get(m2.gloss());
    Gender g1 = getGender(e1);
    Gender g2 = getGender(e2);

    if (!g1.isCompatible(g2)) return;

    Boolean plural1 = getPlural(e1);
    Boolean plural2 = getPlural(e2);
    if (plural1 != plural2) return;

    Speaker s1 = getSpeaker(e1);
    Speaker s2 = getSpeaker(e2);

    if (s1 != s2 && !(s1 == null || s2 == null)) return;
    entities.remove(e2);
    for (Mention m : e2.mentions) {
      m.removeCoreference();
      clusters.put(m.gloss(), e1);
      e1.add(m);
    }
  }

  private void appositives(Document doc, Map<String, Entity> clusters, ArrayList<Entity> entities) {

    List<Mention> allMentions = doc.getMentions();

    // loop through all pairs of mentions that aren't already in the same cluster
    for (Mention m1 : allMentions) {
      Sentence sentence = m1.sentence;
      List<Token> tokens = sentence.tokens;
      for (Mention m2 : allMentions) {
        if (clusters.get(m1.gloss()).equals(clusters.get(m2.gloss()))) continue;

        // "commas" and "is" as separators of PRP and NNP
        // if both mentions are in the same sentence
        if (m2.sentence.equals(sentence)) {

          if (m2.beginIndexInclusive == m1.endIndexExclusive + 1 && (sentence.tokens.get(m1.endIndexExclusive).word().equals(","))) {
            System.out.println("----start");
            System.out.println(sentence);
            System.out.println(m1.gloss());
            System.out.println(m2.gloss());
            System.out.println(tokens.get(m1.headWordIndex).posTag());
            System.out.println(tokens.get(m2.headWordIndex).posTag());
            System.out.println("----end");
            if (tokens.get(m1.headWordIndex).posTag().equals("PRP") && tokens.get(m2.headWordIndex).posTag().equals("NNP")) {
              mergeEntities(m1, m2, clusters, entities);
            }
          }
          if (m1.endIndexExclusive < tokens.size() && tokens.get(m1.endIndexExclusive).word().equals("is") && (m2.beginIndexInclusive - m1.endIndexExclusive) < 4 && (m2.beginIndexInclusive > m1.endIndexExclusive)) {


            if ((tokens.get(m2.headWordIndex).posTag().equals("PRP$") || tokens.get(m2.headWordIndex).posTag().equals("PRP"))
              && (tokens.get(m1.headWordIndex).posTag().equals("NNP") || tokens.get(m1.headWordIndex).posTag().equals("NN"))) {

              mergeEntities(m1, m2, clusters, entities);
            }
          }
        }
      }
    }
  }


  private void fuzzyMatch(Document doc, Map<String, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      Sentence sentence = m1.sentence;
      for (Mention m2 : allMentions) {
        if (clusters.get(m1.gloss()).equals(clusters.get(m2.gloss()))) continue;
        if (m1.gloss().toLowerCase().contains(m2.gloss().toLowerCase())) {
          if ((Pronoun.valueOrNull(m1.headWord()) != null) || (Pronoun.valueOrNull(m2.headWord()) != null)) continue;
          mergeEntities(m1, m2, clusters, entities);
        }
      }
    }
  }

  /**
   * Exact matching
   * @param doc
   */
  private void exactMatch(Document doc, Map<String, Entity> clusters, ArrayList<Entity> entities) {

    //(for each mention...)
    for (Mention m : doc.getMentions()) {
      //(...get its text)
      String mentionString = m.gloss();
      //(...if we've seen this text before...)
      if (clusters.containsKey(mentionString)) {
        Entity toMerge = clusters.get(mentionString);
        m.markCoreferent(toMerge);
      } else {
        //(...else create a new singleton cluster)
        ClusteredMention newCluster = m.markSingleton();
        clusters.put(mentionString, newCluster.entity);
        entities.add(newCluster.entity);
      }
    }
  }

}
