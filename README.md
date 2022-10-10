Create config/manifest.edn and fill it out appropraitely to point at a restored version of mbrainz-1968-1973.

To cause the timeout simply run: (Notice the timeout for Depth=3, Cardinality = 500)
clojure -M -m example.core config/manifest.edn

This example is contrived as, in this case, it never makes sense to do. `?artists2` can simply be droped to produce the same results in this example: 

```
(d/q '[:find (count ?release)
       :in $ [?artists ...] [?artists2 ...]
       :where 
       [?release :release/artists ?artists]
       [?release :release/artists ?artists2]] 
     (d/db conn) artists artists)) ;;(Implies ?artists == ?artists2)
```

However, in the project where I encountered this performance snafu the structure seemed more rational. 

The system uses rules to limit a users access, it would look like: 

```
(d/q '[:find (count ?release)
       :in $ [?artists ...]
       :where 
       (releases-i-can-access? ?release) ;; (Effectively: [?release :release/artists ?artists-i-can-access] ;;(?artists-i-can-access may equal ?artists)
       [?release :release/artists ?artists]] ;;Filter to specific artists in which I am interested
     (d/db conn) artists))
```

Note, I see that this can be resolved by:
 
```
(d/q '[:find (count ?release)
       :in $ [?artists ...]
       :where 
       (artists-i-can-access? ?artists) ;; This will limit the set of artists that moves down to the next clauses, and doesn't cause a ?release to be unified multiple times at all)
       [?release :release/artists ?artists]] 
     (d/db conn) artists))
```

But I would like to understand the performance implications of unifying a variable multple times. 
