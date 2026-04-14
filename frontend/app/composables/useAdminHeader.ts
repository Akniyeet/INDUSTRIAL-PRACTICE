interface AdminHeaderState {
  title: string
  subtitle?: string
}

export const useAdminHeader = () =>
  useState<AdminHeaderState>('adminHeader', () => ({ title: '' }))
