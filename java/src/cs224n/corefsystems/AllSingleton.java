package cs224n.corefsystems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.util.Pair;

public class AllSingleton implements CoreferenceSystem {

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		return;
	}
	
	/*
	 * Add all entities to their own cluster.
	 */
	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
		List<Mention> mentions = doc.getMentions();
		List<ClusteredMention> clustered = new ArrayList<ClusteredMention>();
		for(Mention m : mentions){
			ClusteredMention single = m.markSingleton();
			clustered.add(single);
		}
		return clustered;
	}

}
