/*
 * ============================================================================
 *  SDS Component Library — Lightweight Runtime Barrel
 * ----------------------------------------------------------------------------
 *  Keep the high-frequency, lightweight form primitives on the shared barrel.
 *  Heavier primitives (Modal, SnadLogo, switchers) must be imported from their
 *  canonical direct modules so unrelated routes do not pull their runtimes.
 *  Card and Badge currently have no runtime consumers and remain available by
 *  direct module import if needed in future work.
 * ============================================================================
 */

export {
  Button,
  type ButtonProps,
  type ButtonVariant,
  type ButtonSize,
} from './Button';

export {
  Input,
  type InputProps,
  type TextInputType,
} from './Input';

/* Type-only compatibility exports do not enter the production JS graph. */
export type {
  CardProps,
  CardVariant,
  CardComponentProps,
  LinkCardProps,
} from './Card';

export type {
  ModalProps,
  ModalSize,
} from './Modal';

export type {
  BadgeProps,
  BadgeVariant,
  BadgeSize,
} from './Badge';

export type {
  SnadLogoProps,
  SnadLogoVariant,
  SnadLogoSize,
  SnadLogoTheme,
} from './SnadLogo';
