package cs224n.corefsystems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.util.Pair;

public class OneCluster implements CoreferenceSystem {

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		return;
	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
		List<Mention> mentions = doc.getMentions();
		List<ClusteredMention> clustered = new ArrayList<ClusteredMention>();
		Entity single = new Entity(mentions);
		for(Mention m : mentions){
			ClusteredMention singleClust = m.markCoreferent(single);
			clustered.add(singleClust);
		}
		return clustered;
	}

}
