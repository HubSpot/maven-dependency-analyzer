package org.apache.maven.shared.dependency.analyzer;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.maven.artifact.Artifact;

/**
 * Project dependencies analysis result.
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public class ProjectDependencyAnalysis
{
    // fields -----------------------------------------------------------------

    private final Map<Artifact, Set<DependencyUsage>> usedDeclaredArtifacts;

    private final Map<Artifact, Set<DependencyUsage>> usedUndeclaredArtifacts;

    private final Set<Artifact> unusedDeclaredArtifacts;

    // constructors -----------------------------------------------------------

    public ProjectDependencyAnalysis()
    {
        this( (Set<Artifact>) null, null, null );
    }

    public ProjectDependencyAnalysis( Set<Artifact> usedDeclaredArtifacts, Set<Artifact> usedUndeclaredArtifacts,
                                      Set<Artifact> unusedDeclaredArtifacts )
    {
        this( toMap( usedDeclaredArtifacts ), toMap( usedUndeclaredArtifacts ), unusedDeclaredArtifacts );
    }

    public ProjectDependencyAnalysis( Map<Artifact, Set<DependencyUsage>> usedDeclaredArtifacts,
                                      Map<Artifact, Set<DependencyUsage>> usedUndeclaredArtifacts,
                                      Set<Artifact> unusedDeclaredArtifacts )
    {
        this.usedDeclaredArtifacts = safeCopy( usedDeclaredArtifacts );
        this.usedUndeclaredArtifacts = safeCopy( usedUndeclaredArtifacts );
        this.unusedDeclaredArtifacts = safeCopy( unusedDeclaredArtifacts );
    }

    // public methods ---------------------------------------------------------

    /**
     * Used and declared artifacts.
     * @return {@link Artifact}
     */
    public Set<Artifact> getUsedDeclaredArtifacts()
    {
        return usedDeclaredArtifacts.keySet();
    }

    /**
     * Used and declared artifacts mapped to usages.
     */
    public Map<Artifact, Set<DependencyUsage>> getUsedDeclaredArtifactToUsageMap()
    {
        return usedDeclaredArtifacts;
    }

    /**
     * Used but not declared artifacts.
     * @return {@link Artifact}
     */
    public Set<Artifact> getUsedUndeclaredArtifacts()
    {
        return usedUndeclaredArtifacts.keySet();
    }

    /**
     * Used but not declared artifacts mapped to usages.
     */
    public Map<Artifact, Set<DependencyUsage>> getUsedUndeclaredArtifactToUsageMap()
    {
        return usedUndeclaredArtifacts;
    }

    /**
     * Unused but declared artifacts.
     * @return {@link Artifact}
     */
    public Set<Artifact> getUnusedDeclaredArtifacts()
    {
        return unusedDeclaredArtifacts;
    }

    /**
     * Filter not-compile scoped artifacts from unused declared.
     * 
     * @return updated project dependency analysis
     * @since 1.3
     */
    public ProjectDependencyAnalysis ignoreNonCompile()
    {
        Set<Artifact> filteredUnusedDeclared = new HashSet<Artifact>( unusedDeclaredArtifacts );
        for ( Iterator<Artifact> iter = filteredUnusedDeclared.iterator(); iter.hasNext(); )
        {
            Artifact artifact = iter.next();
            if ( !artifact.getScope().equals( Artifact.SCOPE_COMPILE ) )
            {
                iter.remove();
            }
        }

        return new ProjectDependencyAnalysis( usedDeclaredArtifacts, usedUndeclaredArtifacts, filteredUnusedDeclared );
    }

    /**
     * Force use status of some declared dependencies, to manually fix consequences of bytecode-level analysis which
     * happens to not detect some effective use (constants, annotation with source-retention, javadoc).
     * 
     * @param forceUsedDependencies dependencies to move from "unused-declared" to "used-declared", with
     *            <code>groupId:artifactId</code> format
     * @return updated project dependency analysis
     * @throws ProjectDependencyAnalyzerException if dependencies forced were either not declared or already detected as
     *             used
     * @since 1.3
     */
    public ProjectDependencyAnalysis forceDeclaredDependenciesUsage( String[] forceUsedDependencies )
        throws ProjectDependencyAnalyzerException
    {
        Set<String> forced = new HashSet<String>( Arrays.asList( forceUsedDependencies ) );

        Set<Artifact> forcedUnusedDeclared = new HashSet<Artifact>( unusedDeclaredArtifacts );
        Map<Artifact, Set<DependencyUsage>> forcedUsedDeclared = new HashMap<Artifact, Set<DependencyUsage>>(
            usedDeclaredArtifacts
        );

        for ( Iterator<Artifact> iter = forcedUnusedDeclared.iterator(); iter.hasNext(); )
        {
            Artifact artifact = iter.next();

            if ( forced.remove( artifact.getGroupId() + ':' + artifact.getArtifactId() ) )
            {
                // ok, change artifact status from unused-declared to used-declared
                iter.remove();
                if ( !forcedUsedDeclared.containsKey( artifact ) )
                {
                    forcedUsedDeclared.put( artifact, Collections.<DependencyUsage>emptySet() );
                }
            }
        }

        if ( !forced.isEmpty() )
        {
            // trying to force dependencies as used-declared which were not declared or already detected as used
            Set<String> used = new HashSet<String>();
            for ( Artifact artifact : usedDeclaredArtifacts.keySet() )
            {
                String id = artifact.getGroupId() + ':' + artifact.getArtifactId();
                if ( forced.remove( id ) )
                {
                    used.add( id );
                }
            }

            StringBuilder builder = new StringBuilder();
            if ( !forced.isEmpty() )
            {
                builder.append( "not declared: " ).append( forced );
            }
            if ( !used.isEmpty() )
            {
                if ( builder.length() > 0 )
                {
                    builder.append( " and " );
                }
                builder.append( "declared but already detected as used: " ).append( used );
            }
            throw new ProjectDependencyAnalyzerException( "Trying to force use of dependencies which are " + builder );
        }

        return new ProjectDependencyAnalysis( forcedUsedDeclared, usedUndeclaredArtifacts, forcedUnusedDeclared );
    }

    // Object methods ---------------------------------------------------------

    /*
     * @see java.lang.Object#hashCode()
     */
    public int hashCode()
    {
        int hashCode = getUsedDeclaredArtifacts().hashCode();
        hashCode = ( hashCode * 37 ) + getUsedUndeclaredArtifacts().hashCode();
        hashCode = ( hashCode * 37 ) + getUnusedDeclaredArtifacts().hashCode();

        return hashCode;
    }

    /*
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals( Object object )
    {
        if ( object instanceof ProjectDependencyAnalysis )
        {
            ProjectDependencyAnalysis analysis = (ProjectDependencyAnalysis) object;

            return getUsedDeclaredArtifacts().equals( analysis.getUsedDeclaredArtifacts() )
                && getUsedUndeclaredArtifacts().equals( analysis.getUsedUndeclaredArtifacts() )
                && getUnusedDeclaredArtifacts().equals( analysis.getUnusedDeclaredArtifacts() );
        }

        return false;
    }

    /*
     * @see java.lang.Object#toString()
     */
    public String toString()
    {
        StringBuilder buffer = new StringBuilder();

        if ( !getUsedDeclaredArtifacts().isEmpty() )
        {
            buffer.append( "usedDeclaredArtifacts=" ).append( getUsedDeclaredArtifacts() );
        }

        if ( !getUsedUndeclaredArtifacts().isEmpty() )
        {
            if ( buffer.length() > 0 )
            {
                buffer.append( "," );
            }

            buffer.append( "usedUndeclaredArtifacts=" ).append( getUsedUndeclaredArtifacts() );
        }

        if ( !getUnusedDeclaredArtifacts().isEmpty() )
        {
            if ( buffer.length() > 0 )
            {
                buffer.append( "," );
            }

            buffer.append( "unusedDeclaredArtifacts=" ).append( getUnusedDeclaredArtifacts() );
        }

        buffer.insert( 0, "[" );
        buffer.insert( 0, getClass().getName() );

        buffer.append( "]" );

        return buffer.toString();
    }

    // private methods --------------------------------------------------------

    private <T> Set<T> safeCopy( Set<T> set )
    {
        return ( set == null ) ? Collections.<T>emptySet()
                        : Collections.unmodifiableSet( new LinkedHashSet<T>( set ) );
    }

    private Map<Artifact, Set<DependencyUsage>> safeCopy( Map<Artifact, Set<DependencyUsage>> map )
    {
        Map<Artifact, Set<DependencyUsage>> copy = new LinkedHashMap<Artifact, Set<DependencyUsage>>();

        for ( Entry<Artifact, Set<DependencyUsage>> entry : map.entrySet() )
        {
            copy.put( entry.getKey(), safeCopy( entry.getValue() ) );
        }

        return Collections.unmodifiableMap( copy );
    }

    private static Map<Artifact, Set<DependencyUsage>> toMap( Set<Artifact> set )
    {
        if ( set == null )
        {
            return Collections.<Artifact, Set<DependencyUsage>>emptyMap();
        }
        else
        {
            Map<Artifact, Set<DependencyUsage>> map = new HashMap<Artifact, Set<DependencyUsage>>();

            for ( Artifact artifact : set )
            {
                map.put( artifact, Collections.<DependencyUsage>emptySet() );
            }

            return map;
        }
    }
}
