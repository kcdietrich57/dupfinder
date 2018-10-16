package dup.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Top level container for Context objects representing filesystem trees */
public class RootContext {
	// TODO sort/filter the contexts
	private List<Context> contexts;

	public RootContext() {
		this.contexts = new ArrayList<Context>();
	}

	public RootContext(Collection<Context> contexts) {
		this.contexts = new ArrayList<Context>(contexts);
	}

	public List<Context> getContexts() {
		return this.contexts;
	}

	public void addContext(Context context) {
		this.contexts.add(context);
	}

	public void addContexts(Collection<Context> contexts) {
		this.contexts.addAll(contexts);
	}
}
