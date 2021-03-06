/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.teradata.tempto.internal.fulfillment.table;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.teradata.tempto.fulfillment.table.TableDefinition;
import com.teradata.tempto.fulfillment.table.TableHandle;
import com.teradata.tempto.fulfillment.table.TableManager;
import com.teradata.tempto.fulfillment.table.TableManagerDispatcher;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

public class DefaultTableManagerDispatcher<T extends TableDefinition>
        implements TableManagerDispatcher
{

    private final ListMultimap<Class<? extends TableDefinition>, TableManager> classToTableManagers;
    private final Map<String, TableManager> tableManagers;

    public DefaultTableManagerDispatcher(Map<String, TableManager> tableManagers)
    {
        this.classToTableManagers = ArrayListMultimap.create();
        tableManagers.values().stream().forEach(
                manager -> classToTableManagers.put(manager.getTableDefinitionClass(), manager)
        );
        this.tableManagers = tableManagers;
    }

    @Override
    public TableManager<T> getTableManagerFor(TableDefinition tableDefinition, TableHandle tableHandle)
    {
        Class<? extends TableDefinition> tableDefinitionClass = tableDefinition.getClass();
        if (!classToTableManagers.containsKey(tableDefinitionClass)) {
            throw new IllegalStateException(format("Table manager for table definition class: %s, is not registered", tableDefinitionClass));
        }

        List<TableManager> classTableManagers = classToTableManagers.get(tableDefinitionClass);
        if (classTableManagers.size() == 1)  {
            return getOnlyElement(classTableManagers);
        }

        Optional<String> databaseName = tableHandle.getDatabase();
        if (databaseName.isPresent()) {
            TableManager<T> tableManager = getTableManagerFor(databaseName).orElseThrow(() ->
                            new IllegalStateException(format("No table manager found for database name '%s'.", databaseName.get()))
            );
            return checkTableDefinitionType(tableManager, tableDefinitionClass);
        }

        if (classTableManagers.size() == 0) {
            throw new IllegalStateException(format("No table manager found for table definition class '%s'.", tableDefinitionClass));
        } else {
            throw multipleTableManagersException(tableDefinitionClass, classTableManagers);
        }
    }

    private TableManager<T> checkTableDefinitionType(TableManager<T> tableManager, Class<? extends TableDefinition> tableDefinitionClass)
    {
        checkState(tableManager.getTableDefinitionClass().equals(tableDefinitionClass),
                "Table manager table definition class '%s', does not match requested table definition class '%s'.", tableManager.getTableDefinitionClass(), tableDefinitionClass);
        return tableManager;
    }

    private IllegalStateException multipleTableManagersException(Class<? extends TableDefinition> tableDefinitionClass, List<TableManager> classTableManagers)
    {
        List<String> databaseNames = classTableManagers.stream().map(TableManager::getDatabaseName).collect(toList());
        return new IllegalStateException(format("Multiple databases found for table definition class '%s'. Pick a database from %s",
                tableDefinitionClass, databaseNames));
    }

    private Optional<TableManager<T>> getTableManagerFor(Optional<String> databaseName)
    {
        if (databaseName.isPresent()) {
            return Optional.ofNullable(tableManagers.get(databaseName.get()));
        }
        return Optional.empty();
    }

    @Override
    public Collection<TableManager> getAllTableManagers()
    {
        return tableManagers.values();
    }
}
