import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatNum(n: number): string {
  return Number.isInteger(n) ? n.toString() : n.toFixed(4).replace(/\.?0+$/, '')
}
