// src/types/index.ts

export interface LoginRequest {
  email: string;
  password: string;
}

export interface User {
  id: string;
  email: string;
  full_name: string;
  phone: string | null;
  role: 'STUDENT' | 'ORGANIZER' | 'STAFF';
  status: 'ACTIVE' | 'UNVERIFIED' | 'LOCKED';
  organization_id: string | null;
  student_id: string | null;
  is_temporary_password: boolean;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  refreshExpiresInSeconds: number;
  user: User;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
  metadata: {
    statusCode: number;
    processedBy: string;
    timestamp: string;
  };
  error: {
    type: string;
    title: string;
    detail: string;
    instance: string;
    validationErrors?: string[];
  } | null;
}

export type WorkshopLifecycleStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELED' | 'COMPLETED';
export type WorkshopFrontendStatus = 'AVAILABLE' | 'ALMOST_FULL' | 'SOLD_OUT';

export interface Workshop {
  id: string;
  title: string;
  location: string;
  status: WorkshopLifecycleStatus;
  capacity_total: number;
  capacity_remaining: number;
  price_amount: number;
  start_time: string;
  end_time: string;
  
  // FE-only mock fields
  subtitle: string;
  speaker: string;
  speaker_title: string;
  speaker_bio: string;
  speaker_initials: string;
  urgency_label: string | null;
  ai_summary: string;
  learning_outcomes: string[];
  brochure_size: string;
  viewer_count: number;
}

export type RegistrationStatus = 'PENDING' | 'REGISTERED' | 'CONFIRMED' | 'PAID' | 'FAILED' | 'CANCELED' | 'CHECKED_IN';

export interface Registration {
  id: string;
  workshop_id: string;
  workshop: Workshop;
  status: RegistrationStatus;
  created_at: string;
  qr_code_payload: string;
}

export interface Organization {
  id: string;
  name: string;
  description: string;
  address: string;
  status: 'ACTIVE' | 'INACTIVE';
  organizer_id: string;
  created_at: string;
  updated_at: string;
  
  // FE-only mock fields
  total_workshops: number;
  total_students: number;
  staff_count: number;
  contact_email: string;
  contact_phone: string;
  initials: string;
}
