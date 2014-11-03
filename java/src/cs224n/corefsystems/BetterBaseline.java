package cs224n.corefsystems;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;


import cs224n.coref.*;
import cs224n.util.Pair;

public class BetterBaseline implements CoreferenceSystem {
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
		
		List<ClusteredMention> clusteredMentions = new ArrayList<ClusteredMention>();
		List<Mention> mentions = doc.getMentions();
		Map<String,Entity> clusters = new HashMap<String,Entity>();
		Map<String,Entity> exactMatch = new HashMap<String,Entity>();
		
		for(Mention m : mentions){
			String headWord = m.headWord().toLowerCase();
			Entity e = null;
			if(exactMatch.containsKey(m.gloss())){
				e = exactMatch.get(m.gloss());
				ClusteredMention cm = m.markCoreferent(e);
				clusteredMentions.add(cm);
			}else{
				
				Set<String> co = new HashSet<String>();
				if(coOccuringMentions.containsKey(headWord)){
					co = new HashSet<String>(coOccuringMentions.get(headWord));
				}	
				co.retainAll(clusters.keySet());
				//add new entry to map
				if(co.isEmpty()){
					ClusteredMention cm = m.markSingleton(); 
					e = cm.entity;
					clusteredMentions.add(cm);
				}else{
				//find any paired word and glom on to entity. 
					for(String s : co){
						e = clusters.get(s);
						clusteredMentions.add(m.markCoreferent(e));
						break;
					}
				}
			}
			exactMatch.put(m.gloss(), e);
			clusters.put(headWord,e);
		}
		return clusteredMentions;
	}

}
