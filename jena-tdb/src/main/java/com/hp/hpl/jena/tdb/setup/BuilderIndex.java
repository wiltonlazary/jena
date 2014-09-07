/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.hpl.jena.tdb.setup;

import com.hp.hpl.jena.tdb.base.block.BlockMgr ;
import com.hp.hpl.jena.tdb.base.block.BlockMgrFactory ;
import com.hp.hpl.jena.tdb.base.block.FileMode ;
import com.hp.hpl.jena.tdb.base.file.FileFactory ;
import com.hp.hpl.jena.tdb.base.file.FileSet ;
import com.hp.hpl.jena.tdb.base.objectfile.ObjectFile ;
import com.hp.hpl.jena.tdb.base.record.RecordFactory ;
import com.hp.hpl.jena.tdb.index.Index ;
import com.hp.hpl.jena.tdb.index.IndexParams ;
import com.hp.hpl.jena.tdb.index.RangeIndex ;
import com.hp.hpl.jena.tdb.index.bplustree.BPlusTree ;
import com.hp.hpl.jena.tdb.index.bplustree.BPlusTreeParams ;
import com.hp.hpl.jena.tdb.sys.Names ;

/** Building indexes, blockMgr and files */ 

public class BuilderIndex {
    public static class IndexBuilderStd implements IndexBuilder
    {
        protected BlockMgrBuilder bMgrNodes ;
        protected BlockMgrBuilder bMgrRecords ;
        protected RangeIndexBuilderStd other ;
    
        public IndexBuilderStd(BlockMgrBuilder bMgrNodes, BlockMgrBuilder bMgrRecords) {
            this.bMgrNodes = bMgrNodes ;
            this.bMgrRecords = bMgrRecords ;
            this.other = new RangeIndexBuilderStd(bMgrNodes, bMgrRecords) ;
        }

        @Override
        public Index buildIndex(FileSet fileSet, RecordFactory recordFactory, IndexParams indexParams) {
            // Cheap.
            return other.buildRangeIndex(fileSet, recordFactory, indexParams) ;
        }
    }

    public static class RangeIndexBuilderStd implements RangeIndexBuilder
    {
        private BlockMgrBuilder bMgrNodes ;
        private BlockMgrBuilder bMgrRecords ;

        public RangeIndexBuilderStd(BlockMgrBuilder blockMgrBuilderNodes, BlockMgrBuilder blockMgrBuilderRecords) {
            this.bMgrNodes = blockMgrBuilderNodes ;
            this.bMgrRecords = blockMgrBuilderRecords ;
        }
    
        @Override
        public RangeIndex buildRangeIndex(FileSet fileSet, RecordFactory recordFactory, IndexParams indexParams) {
            int blkSize = indexParams.getBlockSize() ;
            int order = BPlusTreeParams.calcOrder(blkSize, recordFactory.recordLength()) ;
            RangeIndex rIndex = createBPTree(fileSet, order, bMgrNodes, bMgrRecords, recordFactory, indexParams) ;
            return rIndex ;
        }
        
        /** Knowing all the parameters, create a B+Tree */
        private RangeIndex createBPTree(FileSet fileset, int order, 
                                        BlockMgrBuilder blockMgrBuilderNodes,
                                        BlockMgrBuilder blockMgrBuilderRecords,
                                        RecordFactory factory, IndexParams indexParams)
        {
            // ---- Checking
            {
                int blockSize = indexParams.getBlockSize() ;
                if (blockSize < 0 )
                    throw new IllegalArgumentException("Negative blocksize: "+blockSize) ;
                if (blockSize < 0 && order < 0) throw new IllegalArgumentException("Neither blocksize nor order specified") ;
                if (blockSize >= 0 && order < 0) order = BPlusTreeParams.calcOrder(blockSize, factory.recordLength()) ;
                if (blockSize >= 0 && order >= 0)
                {
                    int order2 = BPlusTreeParams.calcOrder(blockSize, factory.recordLength()) ;
                    if (order != order2) 
                        throw new IllegalArgumentException("Wrong order (" + order + "), calculated = " + order2) ;
                }
            }

            BPlusTreeParams params = new BPlusTreeParams(order, factory) ;
            
            BlockMgr blkMgrNodes = blockMgrBuilderNodes.buildBlockMgr(fileset, Names.bptExtTree, indexParams) ;
            BlockMgr blkMgrRecords = blockMgrBuilderRecords.buildBlockMgr(fileset, Names.bptExtRecords, indexParams) ;
            return BPlusTree.create(params, blkMgrNodes, blkMgrRecords) ;
        }
    }

    public static class ObjectFileBuilderStd implements ObjectFileBuilder
    {
        public ObjectFileBuilderStd() { }
        
        @Override
        public ObjectFile buildObjectFile(FileSet fileSet, String ext)
        {
            String filename = fileSet.filename(ext) ;
            if ( fileSet.isMem() )
                return FileFactory.createObjectFileMem(filename) ;
            return FileFactory.createObjectFileDisk(filename) ;
        }
    }

    public static class BlockMgrBuilderStd/*Base*/ implements BlockMgrBuilder
    {
        public BlockMgrBuilderStd() {}
    
        @Override
        public BlockMgr buildBlockMgr(FileSet fileset, String ext, IndexParams indexParams)
        {
            BlockMgr mgr = BlockMgrFactory.create(fileset, ext, indexParams.getBlockSize(), 
                                                  indexParams.getBlockReadCacheSize(), indexParams.getBlockWriteCacheSize()) ;
            return mgr ;
        }
    }

    // Add cache (iff not memory and not 
    // XXX Activate
    public static class BlockMgrBuilderStd2 implements BlockMgrBuilder
    {
        private final BlockMgrBuilder other ;
    
        public BlockMgrBuilderStd2(BlockMgrBuilder other) {
            this.other = other ;
        }
    
        @Override
        public BlockMgr buildBlockMgr(FileSet fileset, String ext, IndexParams indexParams)
        {
            BlockMgr blkMgr = other.buildBlockMgr(fileset, ext, indexParams) ;
            if ( fileset.isMem() )
                return blkMgr ;
            int readCacheSize = indexParams.getBlockReadCacheSize() ;
            int writeCacheSize = indexParams.getBlockWriteCacheSize() ;
            boolean addCache = 
                ! fileset.isMem() && indexParams.getFileMode() != FileMode.mapped ;
            if ( addCache && ( readCacheSize > 0 || writeCacheSize > 0 ) )
                blkMgr = BlockMgrFactory.addCache(blkMgr, readCacheSize, writeCacheSize) ;
            return blkMgr ;
        }
    }

}

