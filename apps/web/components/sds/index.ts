/*
 * ============================================================================
 *  SDS Component Library — Barrel Export
 * ----------------------------------------------------------------------------
 *  Re-exports every SDS primitive so consumers can do:
 *
 *     import { Button, Card, Input, Modal, Badge } from '@/components/sds';
 *
 *  Each component is also available as a named export from its own module
 *  for tree-shaking-friendly imports:
 *
 *     import { Button } from '@/components/sds/Button';
 *
 *  Token + theme assets are NOT exported from here — they are CSS custom
 *  properties resolved at runtime. See:
 *     apps/web/design-system/tokens/theme.css
 * ============================================================================
 */

export {
  Button,
  type ButtonProps,
  type ButtonVariant,
  type ButtonSize,
} from './Button';

export {
  Card,
  LinkCard,
  type CardProps,
  type CardVariant,
  type CardComponentProps,
  type LinkCardProps,
} from './Card';

export {
  Input,
  type InputProps,
  type TextInputType,
} from './Input';

export {
  Modal,
  type ModalProps,
  type ModalSize,
} from './Modal';

export {
  Badge,
  type BadgeProps,
  type BadgeVariant,
  type BadgeSize,
} from './Badge';
