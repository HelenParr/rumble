(:JIQS: ShouldRun; Output="({ "foo" : [ null ] }, { "foo" : [ 1 ] }, { "foo" : [ 2 ] }, { "foo" : [ 3 ] }, { "foo" : [ ] })" :)

declare variable $seq := parallelize(([], [1], [null], [3], [2]));

for $i in $seq
order by $i[] empty greatest
return { "foo" : $i }
