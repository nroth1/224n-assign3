package cs224n.corefsystems;

import java.util.*;

import cs224n.coref.*;
import cs224n.coref.Pronoun.Speaker;
import cs224n.util.Pair;
import cs224n.coref.Sentence.Token;

public class RuleBased2 implements CoreferenceSystem {

  // Map of co-occuring mentions in the training documents
  private HashMap<String, Set<String>> coOccuringMentions = new HashMap<String, Set<String>>();

  // Statistics computed from the document (token-wise distance between mentions of the same sentence)
  private int totalDistance = 0;
  private int n = 0;


  private int distance(Document d, Mention m1, Mention m2) {
    return Math.abs(d.indexOfMention(m1) - d.indexOfMention(m2));
  }

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

    Map<Mention, Entity> clusters = new HashMap<Mention, Entity>();
    ArrayList<Entity> entities = new ArrayList<Entity>();

    // Pass 1: Exact match (extant text)
    exactMatch(doc, clusters, entities);

    // Pass 2: Precise constructs
    constructs(doc, clusters, entities);

    // Pass 3: Exact match (head words)
    headMatch(doc, clusters, entities);


    // Pass 4: Relaxed head matching (head word as a substring of extent text)
//    relaxedHeadMatch(doc, clusters, entities); // does worse...?

    // Pass 5: Pronoun matching (agreement in gender, speaker, singular/plural, etc.)
    pronounMatch(doc, clusters, entities);

    // Pass 6: Co-occurring mentions
//    coOccurring(doc, clusters, entities);

    // Pass 7: Speaker matching
    speakerMatch(doc, clusters, entities);

//    System.out.println(clusters);

    return entitiesToClusters(entities);

  }


  private void mergeEntities(Mention m1, Mention m2, Map<Mention, Entity> clusters, ArrayList<Entity> entities) {
    Entity e1 = clusters.get(m1);
    Entity e2 = clusters.get(m2);
    if (e1.equals(e2)) return;

//    Gender g1 = getGender(e1);
//    Gender g2 = getGender(e2);

//    if (!g1.isCompatible(g2)) return;

//    Boolean plural1 = getPlural(e1);
//    Boolean plural2 = getPlural(e2);
//    if (plural1 != plural2) return;

//    Speaker s1 = getSpeaker(e1);
//    Speaker s2 = getSpeaker(e2);

//    if (s1 != s2 && !(s1 == null || s2 == null)) return;
    entities.remove(e2);
    for (Mention m : e2.mentions) {
      m.removeCoreference();
      clusters.put(m, e1);
      e1.add(m);
    }

//    System.out.println("-----start");
//    System.out.println(m1.sentence);
//    System.out.println(m2.sentence);
//    System.out.println(m1.gloss());
//    System.out.println(m2.gloss());
//    System.out.println("-----end");
  }

  /**
   * Speaker matching
   * @param doc
   * @param clusters
   * @param entities
   */
  private void speakerMatch(Document doc, Map<Mention, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      for (Mention m2 : allMentions) {
        if (clusters.get(m1).equals(clusters.get(m2))) continue;

        if (m1.sentence.equals(m2.sentence) && m1.sentence.tokens.get(m1.headWordIndex).isQuoted() &&
          m1.sentence.tokens.get(m1.headWordIndex).speaker().lastIndexOf(m2.headWord()) != -1 &&
          Pronoun.valueOrNull(m2.headWord()) == null)
        {
//          System.out.println("-----start");
//          System.out.println(m1.sentence);
//          System.out.println(m1.gloss());
//          System.out.println(m2.gloss());
//          System.out.println(m2.headWord());
//          System.out.println(m1.sentence.tokens.get(m1.headWordIndex).speaker());
//          System.out.println("-----end");
          mergeEntities(m1, m2, clusters, entities);
        }
      }
    }
  }


  /**
   * Pronoun matching
   * @param doc
   * @param clusters
   * @param entities
   */
  private void pronounMatch(Document doc, Map<Mention, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      Pronoun p1 = Pronoun.valueOrNull(m1.headWord());

      for (Mention m2 : allMentions) {
        if (clusters.get(m1).equals(clusters.get(m2))) continue;
        Pronoun p2 = Pronoun.valueOrNull(m2.headWord());

        if (p1 == null || p2 == null) continue;

        // First person pronouns
        if (p1.speaker.equals(Speaker.FIRST_PERSON) && p2.speaker.equals(p1.speaker) && p1.plural == p2.plural)
        {
          mergeEntities(m1, m2, clusters, entities);
        }

        // Second person pronouns
        if (p1.speaker.equals(Speaker.SECOND_PERSON) && p2.speaker.equals(p1.speaker))
        {
          mergeEntities(m1, m2, clusters, entities);
        }

        // Third person pronouns
        if (p1.speaker.equals(Speaker.THIRD_PERSON) && p2.speaker.equals(p1.speaker) &&
          p1.gender.isCompatible(p2.gender) && p1.plural == p2.plural)
        {
          mergeEntities(m1, m2, clusters, entities);
        }

//        if (coOccuringMentions.containsKey(m1.headWord()) && coOccuringMentions.get(m1.headWord()).contains(m2.headWord())) {
//          mergeEntities(m1, m2, clusters, entities);
//        }

      }
    }
  }


  /**
   * Relaxed head word matching (case insensitive)
   * @param doc
   * @param clusters
   * @param entities
   */
  private void relaxedHeadMatch(Document doc, Map<Mention, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      for (Mention m2 : allMentions) {
        if (clusters.get(m1).equals(clusters.get(m2))) continue;

        // case insensitive head word matching (except for third person pronouns)
        if (m1.gloss().lastIndexOf(m2.headWord()) != -1 &&
          Pronoun.valueOrNull(m1.headWord()) == null &&
          Pronoun.valueOrNull(m2.headWord()) == null)// &&
//          m1.sentence.equals(m2.sentence))
        {
          mergeEntities(m1, m2, clusters, entities);
        }

//        if (coOccuringMentions.containsKey(m1.headWord()) && coOccuringMentions.get(m1.headWord()).contains(m2.headWord())) {
//          mergeEntities(m1, m2, clusters, entities);
//        }

      }
    }
  }


  /**
   * Exact head word matching (case insensitive)
   * @param doc
   * @param clusters
   * @param entities
   */
  private void headMatch(Document doc, Map<Mention, Entity> clusters, ArrayList<Entity> entities) {
    List<Mention> allMentions = doc.getMentions();
    for (Mention m1 : allMentions) {
      for (Mention m2 : allMentions) {
        if (clusters.get(m1).equals(clusters.get(m2))) continue;

        Pronoun p1, p2;

        // case insensitive head word matching (except for third person pronouns)
        if (m1.headWord().equalsIgnoreCase(m2.headWord()) &&
          !((p1 = Pronoun.valueOrNull(m1.headWord())) != null && p1.speaker.equals(Speaker.THIRD_PERSON)) &&
          !((p2 = Pronoun.valueOrNull(m2.headWord())) != null && p2.speaker.equals(Speaker.THIRD_PERSON)))
        {
          mergeEntities(m1, m2, clusters, entities);

        }
      }
    }
  }

  /**
   * Constructs (appositives, predicate nominatives, etc.)
   * @param doc
   * @param clusters
   * @param entities
   */
  private void constructs(Document doc, Map<Mention, Entity> clusters, ArrayList<Entity> entities) {

    List<Mention> allMentions = doc.getMentions();

    // loop through all pairs of mentions that aren't already in the same cluster
    for (Mention m1 : allMentions) {
      Sentence sentence = m1.sentence;
      List<Token> tokens = sentence.tokens;
      String posTag1 = tokens.get(m1.headWordIndex).posTag();
      for (Mention m2 : allMentions) {
        if (clusters.get(m1).equals(clusters.get(m2))) continue;

        // if both mentions are in the same sentence
        if (m2.sentence.equals(sentence)) {

          // 1. appositives (",")
          String posTag2 = tokens.get(m2.headWordIndex).posTag();

          if (m2.beginIndexInclusive == m1.endIndexExclusive + 1 &&
            (sentence.tokens.get(m1.endIndexExclusive).word().equals(",")))
          {
            if ((posTag1.equals("PRP") && posTag2.equals("NNP")) ||
              (posTag1.equals("NNS") && posTag2.equals("DT")))
            {
              mergeEntities(m1, m2, clusters, entities);
            }
            Pronoun p2;
            if (posTag1.equals("NNP") && posTag2.equals("PRP") && !Name.isName(m1.headWord()) &&
              Pronoun.valueOrNull(m1.headWord()) == null &&
              (p2 = Pronoun.valueOrNull(m2.headWord())) != null)
            {
              if (p2.speaker.equals(Speaker.THIRD_PERSON) && !p2.gender.isAnimate())
              {
                mergeEntities(m1, m2, clusters, entities);
              }
            }
          }

          // 2. predicate nominatives ("is")
          if (m1.endIndexExclusive < tokens.size() &&
            tokens.get(m1.endIndexExclusive).word().equals("is") &&
            (m2.beginIndexInclusive - m1.endIndexExclusive) < 12 &&
            (m2.beginIndexInclusive > m1.endIndexExclusive))
          {
            Pronoun p2;
            if ((p2 = Pronoun.valueOrNull(m2.headWord())) != null)
            {
              if (p2.speaker.equals(Speaker.THIRD_PERSON) &&
                (!p2.gender.isAnimate() || p2.gender.equals(Gender.EITHER)) &&
                Pronoun.valueOrNull(m1.headWord()) == null)
              {
                mergeEntities(m1, m2, clusters, entities);
              }
            }
            if (tokens.get(m1.headWordIndex).posTag().equals("NNP") && tokens.get(m2.headWordIndex).posTag().equals("PRP$"))
            {
              mergeEntities(m1, m2, clusters, entities);
            }
          }

          // 3. acronyms - account for apostrophe's (ex. "USTC" vs. "USTC's")  and articles (ex. "UN" and "the UN")
          if (m1.gloss().toUpperCase().equals(m1.gloss()) && !m1.gloss().equals("I") &&
            m2.gloss().lastIndexOf(m1.gloss()) != -1 &&
            m2.gloss().length() - m1.gloss().length() < 5)
          {
            mergeEntities(m1, m2, clusters, entities);
          }
        }
      }
    }
  }

  /**
   * Exact matching of extent text
   * @param doc
   * @param clusters
   * @param entities
   */
  private void exactMatch(Document doc, Map<Mention, Entity> clusters, ArrayList<Entity> entities) {

    Map<String, Entity> clusterNames = new HashMap<String, Entity>();

    // loop through mentions
    for (Mention m : doc.getMentions()) {
      String mentionString = m.gloss();
      // if we've seen this extent text, and the head word is not a pronoun
      if (clusterNames.containsKey(mentionString.toLowerCase()) &&
        (Pronoun.valueOrNull(m.headWord())) == null) {
        // merge clusters
        Entity toMerge = clusterNames.get(mentionString.toLowerCase());
        m.markCoreferent(toMerge);
        clusters.put(m, toMerge);
      } else {
        // create new singleton cluster
        ClusteredMention newCluster = m.markSingleton();
        clusterNames.put(mentionString.toLowerCase(), newCluster.entity);
        clusters.put(m, newCluster.entity);
        entities.add(newCluster.entity);
      }
    }
  }

}
