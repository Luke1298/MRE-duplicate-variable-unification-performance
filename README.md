## Preface ##

While it's [clear](https://docs.datomic.com/cloud/best.html#most-selective-clauses-first) and intuitive that when writing a query the most selective clauses should be first in a query, the performance implications of having multiple clauses of effectively the same selectivity is not clear.

In this example we demonstrate that multiple identically selective clauses can have a drastically adverse effect on query performance.
We use the [mbrainz-1968-1973](https://github.com/Datomic/mbrainz-importer) example data set for demonstration purposes.

## Methodology ##

For demonstration purposes we'll use a set number of artists "cardinality" and query for all the releases which belong to those artists "depth" times.


ie. Our contrived queries look like this:

```
(d/q '[:find (count ?release)
       :in $ [?artists0 ...] [?artists1 ...] ;;"depth" times
       :where
       [?release :release/artists ?artists0]
       [?release :release/artists ?artists1]
       ;;"depth" times
      ]
      (d/db conn) artists artists ;;artists is repeated "depth" times
)
```
Where:
;;(count artists) == cardinality

This example is of course contrived as, in this case it'd be trivial toe remove `?artists1` and the same results would be produced.

## <a name="not-so-contrived"></a>When this might not be so contrived ##

In the project where I encountered this performance snafu the structure seemed more rational.

The system uses [datomic rules](https://docs.datomic.com/cloud/query/query-data-reference.html#rules) to limit a users access.

So assuming that mbrainz had some concept of rules to grant access our use case would look more like:

```
(d/q '[:find (count ?release)
       :in $ [?artists ...]
       :where
       (releases-i-can-access? ?release) ;; (Effectively: [?release :release/artists ?artists-i-can-access] ;;(?artists-i-can-access may equal ?artists)
       [?release :release/artists ?artists]] ;;Filter to specific artists in which I am interested
     (d/db conn) artists)
```

## Running the default test suite: ##
Create config/manifest.edn and fill it out appropriately to point at a restored version of mbrainz-1968-1973.



To cause the timeout simply run: (Notice the timeout for Depth=3 (or 4), Cardinality = 500):
```
clojure -M -m example.core config/manifest.edn
```


However, in the project where I encountered this performance snafu the structure seemed more rational.

The system uses rules to limit a users access.

So our use case would look more like:

```
(d/q '[:find (count ?release)
       :in $ [?artists ...]
       :where
       (releases-i-can-access? ?release) ;; (Effectively: [?release :release/artists ?artists-i-can-access] ;;(?artists-i-can-access may equal ?artists)
       [?release :release/artists ?artists]] ;;Filter to specific artists in which I am interested
     (d/db conn) artists)
```

## Findings ##
Here are some data points which I measured against the mbrainz data set stored in and queried against a Datomic Cloud instance whose DatomicCloudVersion was 9095.
I understand that the scope of this may not be all that useful, but I figured I'd include it as a starting point to demonstrate the behavior I am seeing.

These tests can be replicated directly by including a parameter specifying the test suite when running 'example.core'.
ie.
```
clojure -M -m example.core config/manifest.edn fixed-cardinality
```
or
```
clojure -M -m example.core config/manifest.edn fixed-depth
```


It seems that given a fixed cardinality that the time is linear in depth.

Timing (in milliseconds) ≈ 67 \* depth + 61 ;;Linear regression fits this fairly well: R^2 = 0.956
| Cardinality | Depth       | Timing      |
| ----------- | ----------- | ----------- |
| 100         | 1           | 90.300 ms   |
| 100         | 2           | 201.400 ms  |
| 100         | 3           | 236.600 ms  |
| 100         | 4           | 357.400 ms  |
| 100         | 5           | 508.900 ms  |
| 100         | 6           | 456.400 ms  |
| 100         | 7           | 491.300 ms  |
| 100         | 8           | 597.500 ms  |
| 100         | 9           | 659.100 ms  |
| 100         | 10          | 735.500 ms  |

The more concerning thing is performance for a fixed depth (in [this section](#not-so-contrived) I demonstrate why a depth = 2 may not be totally contrived) as cardinality varies there seems to be an approximately quadratic relationship with time.

Timing (in milliseconds) ≈ 0.308 \* (cardinality/50)\*\*2 - 8.308\*(cardinality/50) + 722.69 ;;Quadratic regression fits this fairly well: R^2 = 0.993
| Cardinality | Depth       | Timing      |
| ----------- | ----------- | ----------- |
|  50         | 2           | 117.300 ms  |
| 100         | 2           | 170.000 ms  |
| 150         | 2           | 346.400 ms  |
| 200         | 2           | 469.400 ms  |
| 250         | 2           | 798.500 ms  |
| 300         | 2           | 1108.300 ms |
| 350         | 2           | 1577.100 ms |
| 400         | 2           | 2212.900 ms |
| 450         | 2           | 3099.800 ms |
| 500         | 2           | 3933.700 ms |
| 550         | 2           | 5476.200 ms |
| 600         | 2           | 6735.600 ms |
| 650         | 2           | 8603.500 ms |

### Short comings: ###
It may not actually be that useful to cite "cardinality" as the number of artists under consideration, as artist won't have a constant number of releases in the dataset.

## Known resolution ##

Considering the access rule pattern from [this section](#not-so-contrived) assuming that release access can be re-written as artists access the first clause could simply limit ?artists to those artists which you have access.

```
(d/q '[:find (count ?release)
       :in $ [?artists ...]
       :where
       (artists-i-can-access? ?artists) ;; This will limit the set of artists that moves down to the next clauses, and doesn't cause a ?release to be unified multiple times at all)
       [?release :release/artists ?artists]]
     (d/db conn) artists)
```

However, I would like to understand the underlying performance implications of unifying a variable multiple times.
