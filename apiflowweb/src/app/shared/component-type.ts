import { ComponentType } from '../core/models';

/** Material Symbols icon and label for each architectural role. */
export const COMPONENT_TYPES: Record<ComponentType, { icon: string; label: string }> = {
  CONTROLLER: { icon: 'api', label: 'Controller' },
  USE_CASE: { icon: 'account_tree', label: 'Use case' },
  SERVICE: { icon: 'settings_suggest', label: 'Service' },
  REPOSITORY: { icon: 'inventory_2', label: 'Repository' },
  DATABASE: { icon: 'database', label: 'Database' },
};
