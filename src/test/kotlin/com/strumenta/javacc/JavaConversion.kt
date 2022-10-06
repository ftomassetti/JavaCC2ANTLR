package com.strumenta.javacc

import com.strumenta.kolasu.parsing.ParseTreeLeaf
import com.strumenta.kolasu.parsing.ParseTreeNode
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.tree.TerminalNode
import org.javacc.parser.JavaCCGlobals
import org.javacc.parser.Main as javacc
import org.junit.Before
import org.junit.BeforeClass
import org.slf4j.LoggerFactory
import org.snt.inmemantlr.GenericParser
import java.io.File
import kotlin.test.assertEquals
import org.junit.Test as test

internal class SpecialClassLoader(parent: ClassLoader) : ClassLoader(parent) {

    private val m = HashMap<String, ByteArray>()

    /**
     * find a class that is already loaded
     *
     * @param name class name
     * @return the actual class
     * @throws ClassNotFoundException if the class could not be found
     */
    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*> {
        var mbc: ByteArray? = m[name]
        if (mbc == null) {
            mbc = m[name.replace(".", "/")]
            if (mbc == null) {
                LOGGER.error("Could not find {}", name)
                return super.findClass(name)
            }
        }
        val bseq = mbc
        return defineClass(name, bseq, 0, bseq.size)
    }

    /**
     * add class to class loader
     */
    fun addClass(className: String, bytes: ByteArray) {
        m[className] = bytes
    }

    companion object {

        private val LOGGER = LoggerFactory.getLogger(SpecialClassLoader::class.java)
    }

}

fun toParseTree(node: ParserRuleContext, vocabulary: Vocabulary) : ParseTreeNode {
    val res = ParseTreeNode(node.javaClass.simpleName.removeSuffix("Context"))
    node.children?.forEach { c ->
        when (c) {
            is ParserRuleContext -> res.child(toParseTree(c, vocabulary))
            is TerminalNode -> res.child(ParseTreeLeaf(vocabulary.getSymbolicName(c.symbol.type), c.text))
        }
    }
    return res
}

class JavaGrammarTest {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(JavaGrammarTest::class.java)

        private lateinit var genericParser : GenericParser
        private lateinit var vocabulary : Vocabulary
        private var succeeded = 0
        private val quarantinedFiles = setOf(
            "src/test/resources/guava-src/com/google/common/base/Ascii.java",
            "src/test/resources/guava-src/com/google/common/base/CaseFormat.java",
            "src/test/resources/guava-src/com/google/common/base/CharMatcher.java",
            "src/test/resources/guava-src/com/google/common/base/Defaults.java",
            "src/test/resources/guava-src/com/google/common/base/Enums.java",
            "src/test/resources/guava-src/com/google/common/base/Equivalence.java",
            "src/test/resources/guava-src/com/google/common/base/FinalizableReferenceQueue.java",
            "src/test/resources/guava-src/com/google/common/base/internal/Finalizer.java",
            "src/test/resources/guava-src/com/google/common/base/Joiner.java",
            "src/test/resources/guava-src/com/google/common/base/MoreObjects.java",
            "src/test/resources/guava-src/com/google/common/base/Optional.java",
            "src/test/resources/guava-src/com/google/common/base/PairwiseEquivalence.java",
            "src/test/resources/guava-src/com/google/common/base/Platform.java",
            "src/test/resources/guava-src/com/google/common/base/Preconditions.java",
            "src/test/resources/guava-src/com/google/common/base/Predicates.java",
            "src/test/resources/guava-src/com/google/common/base/SmallCharMatcher.java",
            "src/test/resources/guava-src/com/google/common/base/Splitter.java",
            "src/test/resources/guava-src/com/google/common/base/Utf8.java",
            "src/test/resources/guava-src/com/google/common/cache/CacheBuilderSpec.java",
            "src/test/resources/guava-src/com/google/common/cache/LocalCache.java",
            "src/test/resources/guava-src/com/google/common/cache/Striped64.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractBiMap.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractListMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractMapBasedMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractMapBasedMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractNavigableMap.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractSetMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractSortedKeySortedSetMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractSortedMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractSortedSetMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/AbstractTable.java",
            "src/test/resources/guava-src/com/google/common/collect/ArrayListMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/ArrayListMultimapGwtSerializationDependencies.java",
            "src/test/resources/guava-src/com/google/common/collect/ArrayTable.java",
            "src/test/resources/guava-src/com/google/common/collect/CartesianList.java",
            "src/test/resources/guava-src/com/google/common/collect/CollectCollectors.java",
            "src/test/resources/guava-src/com/google/common/collect/Collections2.java",
            "src/test/resources/guava-src/com/google/common/collect/CollectSpliterators.java",
            "src/test/resources/guava-src/com/google/common/collect/CompactHashMap.java",
            "src/test/resources/guava-src/com/google/common/collect/CompactHashSet.java",
            "src/test/resources/guava-src/com/google/common/collect/CompactLinkedHashMap.java",
            "src/test/resources/guava-src/com/google/common/collect/Comparators.java",
            "src/test/resources/guava-src/com/google/common/collect/CompoundOrdering.java",
            "src/test/resources/guava-src/com/google/common/collect/ConcurrentHashMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/Cut.java",
            "src/test/resources/guava-src/com/google/common/collect/DenseImmutableTable.java",
            "src/test/resources/guava-src/com/google/common/collect/DescendingMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/EmptyImmutableListMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/EmptyImmutableSetMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/EnumBiMap.java",
            "src/test/resources/guava-src/com/google/common/collect/EnumMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/FilteredEntryMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/FilteredEntrySetMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/FilteredKeyMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/FilteredKeySetMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/FilteredMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/FilteredMultimapValues.java",
            "src/test/resources/guava-src/com/google/common/collect/FluentIterable.java",
            "src/test/resources/guava-src/com/google/common/collect/ForwardingMap.java",
            "src/test/resources/guava-src/com/google/common/collect/ForwardingMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/ForwardingMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/ForwardingNavigableMap.java",
            "src/test/resources/guava-src/com/google/common/collect/ForwardingSetMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/ForwardingSortedMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/ForwardingTable.java",
            "src/test/resources/guava-src/com/google/common/collect/GeneralRange.java",
            "src/test/resources/guava-src/com/google/common/collect/HashBasedTable.java",
            "src/test/resources/guava-src/com/google/common/collect/HashBiMap.java",
            "src/test/resources/guava-src/com/google/common/collect/HashMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/HashMultimapGwtSerializationDependencies.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableBiMap.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableBiMapFauxverideShim.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableCollection.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableEnumMap.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableEnumSet.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableList.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableListMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableMap.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableMapEntrySet.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableMapValues.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableRangeMap.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableRangeSet.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableSet.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableSetMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableSortedMap.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableSortedMapFauxverideShim.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableSortedMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableSortedMultisetFauxverideShim.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableSortedSet.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableSortedSetFauxverideShim.java",
            "src/test/resources/guava-src/com/google/common/collect/ImmutableTable.java",
            "src/test/resources/guava-src/com/google/common/collect/Iterables.java",
            "src/test/resources/guava-src/com/google/common/collect/Iterators.java",
            "src/test/resources/guava-src/com/google/common/collect/LexicographicalOrdering.java",
            "src/test/resources/guava-src/com/google/common/collect/LinkedHashMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/LinkedHashMultimapGwtSerializationDependencies.java",
            "src/test/resources/guava-src/com/google/common/collect/LinkedListMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/ListMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/Lists.java",
            "src/test/resources/guava-src/com/google/common/collect/MapDifference.java",
            "src/test/resources/guava-src/com/google/common/collect/MapMakerInternalMap.java",
            "src/test/resources/guava-src/com/google/common/collect/Maps.java",
            "src/test/resources/guava-src/com/google/common/collect/MinMaxPriorityQueue.java",
            "src/test/resources/guava-src/com/google/common/collect/MoreCollectors.java",
            "src/test/resources/guava-src/com/google/common/collect/Multimap.java",
            "src/test/resources/guava-src/com/google/common/collect/MultimapBuilder.java",
            "src/test/resources/guava-src/com/google/common/collect/Multimaps.java",
            "src/test/resources/guava-src/com/google/common/collect/Multiset.java",
            "src/test/resources/guava-src/com/google/common/collect/Multisets.java",
            "src/test/resources/guava-src/com/google/common/collect/MutableClassToInstanceMap.java",
            "src/test/resources/guava-src/com/google/common/collect/Ordering.java",
            "src/test/resources/guava-src/com/google/common/collect/Range.java",
            "src/test/resources/guava-src/com/google/common/collect/RangeSet.java",
            "src/test/resources/guava-src/com/google/common/collect/RegularImmutableBiMap.java",
            "src/test/resources/guava-src/com/google/common/collect/RegularImmutableMap.java",
            "src/test/resources/guava-src/com/google/common/collect/RegularImmutableMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/RegularImmutableTable.java",
            "src/test/resources/guava-src/com/google/common/collect/RowSortedTable.java",
            "src/test/resources/guava-src/com/google/common/collect/Serialization.java",
            "src/test/resources/guava-src/com/google/common/collect/SetMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/Sets.java",
            "src/test/resources/guava-src/com/google/common/collect/SingletonImmutableBiMap.java",
            "src/test/resources/guava-src/com/google/common/collect/SingletonImmutableList.java",
            "src/test/resources/guava-src/com/google/common/collect/SingletonImmutableSet.java",
            "src/test/resources/guava-src/com/google/common/collect/SingletonImmutableTable.java",
            "src/test/resources/guava-src/com/google/common/collect/SortedLists.java",
            "src/test/resources/guava-src/com/google/common/collect/SortedMapDifference.java",
            "src/test/resources/guava-src/com/google/common/collect/SortedMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/SortedSetMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/SparseImmutableTable.java",
            "src/test/resources/guava-src/com/google/common/collect/StandardRowSortedTable.java",
            "src/test/resources/guava-src/com/google/common/collect/StandardTable.java",
            "src/test/resources/guava-src/com/google/common/collect/Streams.java",
            "src/test/resources/guava-src/com/google/common/collect/Synchronized.java",
            "src/test/resources/guava-src/com/google/common/collect/Table.java",
            "src/test/resources/guava-src/com/google/common/collect/Tables.java",
            "src/test/resources/guava-src/com/google/common/collect/TopKSelector.java",
            "src/test/resources/guava-src/com/google/common/collect/TreeBasedTable.java",
            "src/test/resources/guava-src/com/google/common/collect/TreeMultimap.java",
            "src/test/resources/guava-src/com/google/common/collect/TreeMultiset.java",
            "src/test/resources/guava-src/com/google/common/collect/TreeRangeMap.java",
            "src/test/resources/guava-src/com/google/common/collect/TreeRangeSet.java",
            "src/test/resources/guava-src/com/google/common/collect/TreeTraverser.java",
            "src/test/resources/guava-src/com/google/common/collect/WellBehavedMap.java",
            "src/test/resources/guava-src/com/google/common/eventbus/Dispatcher.java",
            "src/test/resources/guava-src/com/google/common/eventbus/EventBus.java",
            "src/test/resources/guava-src/com/google/common/eventbus/SubscriberRegistry.java",
            "src/test/resources/guava-src/com/google/common/graph/AbstractBaseGraph.java",
            "src/test/resources/guava-src/com/google/common/graph/AbstractNetwork.java",
            "src/test/resources/guava-src/com/google/common/graph/AbstractValueGraph.java",
            "src/test/resources/guava-src/com/google/common/graph/BaseGraph.java",
            "src/test/resources/guava-src/com/google/common/graph/ConfigurableNetwork.java",
            "src/test/resources/guava-src/com/google/common/graph/ConfigurableValueGraph.java",
            "src/test/resources/guava-src/com/google/common/graph/DirectedGraphConnections.java",
            "src/test/resources/guava-src/com/google/common/graph/DirectedMultiNetworkConnections.java",
            "src/test/resources/guava-src/com/google/common/graph/ElementOrder.java",
            "src/test/resources/guava-src/com/google/common/graph/EndpointPairIterator.java",
            "src/test/resources/guava-src/com/google/common/graph/Graph.java",
            "src/test/resources/guava-src/com/google/common/graph/ImmutableGraph.java",
            "src/test/resources/guava-src/com/google/common/graph/ImmutableNetwork.java",
            "src/test/resources/guava-src/com/google/common/graph/ImmutableValueGraph.java",
            "src/test/resources/guava-src/com/google/common/graph/MapIteratorCache.java",
            "src/test/resources/guava-src/com/google/common/graph/MultiEdgesConnecting.java",
            "src/test/resources/guava-src/com/google/common/graph/Traverser.java",
            "src/test/resources/guava-src/com/google/common/graph/UndirectedMultiNetworkConnections.java",
            "src/test/resources/guava-src/com/google/common/graph/ValueGraph.java",
            "src/test/resources/guava-src/com/google/common/hash/AbstractHasher.java",
            "src/test/resources/guava-src/com/google/common/hash/BloomFilter.java",
            "src/test/resources/guava-src/com/google/common/hash/BloomFilterStrategies.java",
            "src/test/resources/guava-src/com/google/common/hash/Crc32cHashFunction.java",
            "src/test/resources/guava-src/com/google/common/hash/FarmHashFingerprint64.java",
            "src/test/resources/guava-src/com/google/common/hash/Funnels.java",
            "src/test/resources/guava-src/com/google/common/hash/HashCode.java",
            "src/test/resources/guava-src/com/google/common/hash/Hashing.java",
            "src/test/resources/guava-src/com/google/common/hash/LittleEndianByteArray.java",
            "src/test/resources/guava-src/com/google/common/hash/Murmur3_128HashFunction.java",
            "src/test/resources/guava-src/com/google/common/hash/Murmur3_32HashFunction.java",
            "src/test/resources/guava-src/com/google/common/hash/Striped64.java",
            "src/test/resources/guava-src/com/google/common/html/HtmlEscapers.java",
            "src/test/resources/guava-src/com/google/common/io/BaseEncoding.java",
            "src/test/resources/guava-src/com/google/common/io/Files.java",
            "src/test/resources/guava-src/com/google/common/io/LineBuffer.java",
            "src/test/resources/guava-src/com/google/common/io/LittleEndianDataOutputStream.java",
            "src/test/resources/guava-src/com/google/common/io/MoreFiles.java",
            "src/test/resources/guava-src/com/google/common/io/Resources.java",
            "src/test/resources/guava-src/com/google/common/math/BigIntegerMath.java",
            "src/test/resources/guava-src/com/google/common/math/DoubleMath.java",
            "src/test/resources/guava-src/com/google/common/math/DoubleUtils.java",
            "src/test/resources/guava-src/com/google/common/math/IntMath.java",
            "src/test/resources/guava-src/com/google/common/math/LongMath.java",
            "src/test/resources/guava-src/com/google/common/math/Quantiles.java",
            "src/test/resources/guava-src/com/google/common/net/HostAndPort.java",
            "src/test/resources/guava-src/com/google/common/net/InetAddresses.java",
            "src/test/resources/guava-src/com/google/common/net/InternetDomainName.java",
            "src/test/resources/guava-src/com/google/common/net/MediaType.java",
            "src/test/resources/guava-src/com/google/common/net/PercentEscaper.java",
            "src/test/resources/guava-src/com/google/common/primitives/Booleans.java",
            "src/test/resources/guava-src/com/google/common/primitives/Bytes.java",
            "src/test/resources/guava-src/com/google/common/primitives/Chars.java",
            "src/test/resources/guava-src/com/google/common/primitives/Doubles.java",
            "src/test/resources/guava-src/com/google/common/primitives/Floats.java",
            "src/test/resources/guava-src/com/google/common/primitives/ImmutableDoubleArray.java",
            "src/test/resources/guava-src/com/google/common/primitives/ImmutableIntArray.java",
            "src/test/resources/guava-src/com/google/common/primitives/ImmutableLongArray.java",
            "src/test/resources/guava-src/com/google/common/primitives/Ints.java",
            "src/test/resources/guava-src/com/google/common/primitives/Longs.java",
            "src/test/resources/guava-src/com/google/common/primitives/ParseRequest.java",
            "src/test/resources/guava-src/com/google/common/primitives/Primitives.java",
            "src/test/resources/guava-src/com/google/common/primitives/Shorts.java",
            "src/test/resources/guava-src/com/google/common/primitives/UnsignedBytes.java",
            "src/test/resources/guava-src/com/google/common/primitives/UnsignedInts.java",
            "src/test/resources/guava-src/com/google/common/primitives/UnsignedLongs.java",
            "src/test/resources/guava-src/com/google/common/reflect/ClassPath.java",
            "src/test/resources/guava-src/com/google/common/reflect/Invokable.java",
            "src/test/resources/guava-src/com/google/common/reflect/MutableTypeToInstanceMap.java",
            "src/test/resources/guava-src/com/google/common/reflect/Reflection.java",
            "src/test/resources/guava-src/com/google/common/reflect/TypeResolver.java",
            "src/test/resources/guava-src/com/google/common/reflect/Types.java",
            "src/test/resources/guava-src/com/google/common/reflect/TypeToken.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/AbstractCatchingFuture.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/AbstractTransformFuture.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/AggregateFuture.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/AggregateFutureState.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/AtomicDoubleArray.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/CollectionFuture.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/CombinedFuture.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/CycleDetectingLockFactory.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/ForwardingExecutorService.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/Futures.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/FuturesGetChecked.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/ListenerCallQueue.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/ListeningExecutorService.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/MoreExecutors.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/ServiceManager.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/Striped.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/TrustedListenableFutureTask.java",
            "src/test/resources/guava-src/com/google/common/util/concurrent/WrappingExecutorService.java",
            "src/test/resources/guava-src/com/google/common/xml/XmlEscapers.java",
            "src/test/resources/guava-src/com/google/thirdparty/publicsuffix/PublicSuffixType.java",
            "src/test/resources/guava-src/com/google/thirdparty/publicsuffix/TrieParser.java")

        @BeforeClass
        @JvmStatic fun setup() {
            val file = File("src/test/resources/java.jj")
            val grammarName = file.nameWithoutExtension.replaceFirstChar(Char::titlecase)

            JavaCCGlobals.reInit()
            val javaCCGrammar = loadJavaCCGrammar(file)
            val antlrGrammar = javaCCGrammar.convertToAntlr(grammarName)
            this.genericParser = antlrGrammar.genericParser()
            val element = genericParser.allCompiledObjects.find { it.isLexer }
            val specialClassLoader = SpecialClassLoader(this::class.java.classLoader)
            element!!.byteCodeObjects.forEach { specialClassLoader.addClass(it.className, it.bytes) }
            val cl = specialClassLoader.loadClass("JavaLexer")
            this.vocabulary = cl.getField("VOCABULARY").get(null) as Vocabulary
        }

        @Before
        fun setupMethod() {
            javacc.reInitAll()
        }

    }

    @test
    fun loadJavaParser() {
        val code = "class A { }"
        val ast = genericParser.parse(code)
        val parseTree = toParseTree(ast, vocabulary)
        assertEquals("CompilationUnit\n" +
                "  Modifiers\n" +
                "  ClassOrInterfaceDeclaration\n" +
                "    T:CLASS[class]\n" +
                "    SimpleName\n" +
                "      Identifier\n" +
                "        T:IDENTIFIER[A]\n" +
                "    ClassOrInterfaceBody\n" +
                "      T:LBRACE[{]\n" +
                "      T:RBRACE[}]\n" +
                "  T:EOF[<EOF>]\n", parseTree.multiLineString())
    }

    @test
    fun canParseAllGuava() {
        val guavaSrc = File("src/test/resources/guava-src")
        parseDir(guavaSrc)
        LOGGER.warn("Skipped {} quarantined Java files ({} succeeded)", quarantinedFiles.size, succeeded)
    }

    private fun parseDir(src: File) {
        src.listFiles().forEach {
            if (it.isDirectory) {
                parseDir(it)
            } else if (it.isFile && it.extension == "java") {
                try {
                    parseJavaFile(it)
                    if (quarantinedFiles.contains(it.toString())) {
                        throw RuntimeException("Quarantined file $it succeeded, please update quarantined list")
                    }
                } catch (e: Exception) {
                    if (!quarantinedFiles.contains(it.toString())) {
                        throw e
                    }
                }
            }
        }
    }

    private fun parseJavaFile(javaFile: File) {
        println("Parsing $javaFile")
        try {
            genericParser.parse(javaFile)
            succeeded++
        } catch (e: Exception) {
            throw RuntimeException("Issue parsing $javaFile", e)
        }
    }
}