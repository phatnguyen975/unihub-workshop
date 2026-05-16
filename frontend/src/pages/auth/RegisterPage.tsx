import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Eye, EyeOff, BookOpen } from 'lucide-react';
import { SuccessModal } from '../../components/common/SuccessModal';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_PATTERN = /^[\d\s()+-]+$/;
const MIN_PASSWORD_LENGTH = 8;

type RegisterErrors = {
  fullName?: string;
  studentId?: string;
  email?: string;
  phone?: string;
  password?: string;
  confirmPassword?: string;
};

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [studentId, setStudentId] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isSuccessOpen, setIsSuccessOpen] = useState(false);
  const [errors, setErrors] = useState<RegisterErrors>({});

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    const nextErrors: RegisterErrors = {};
    const trimmedFullName = fullName.trim();
    const trimmedStudentId = studentId.trim();
    const trimmedEmail = email.trim();
    const trimmedPhone = phone.trim();

    if (!trimmedFullName) {
      nextErrors.fullName = 'Full name is required.';
    }

    if (!trimmedStudentId) {
      nextErrors.studentId = 'Student ID is required.';
    }

    if (!trimmedEmail) {
      nextErrors.email = 'Email is required.';
    } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
      nextErrors.email = 'Enter a valid email address.';
    }

    if (!trimmedPhone) {
      nextErrors.phone = 'Phone number is required.';
    } else if (!PHONE_PATTERN.test(trimmedPhone)) {
      nextErrors.phone = 'Enter a valid phone number.';
    }

    if (!password) {
      nextErrors.password = 'Password is required.';
    } else if (password.length < MIN_PASSWORD_LENGTH) {
      nextErrors.password = `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`;
    }

    if (!confirmPassword) {
      nextErrors.confirmPassword = 'Please confirm your password.';
    } else if (password !== confirmPassword) {
      nextErrors.confirmPassword = 'Passwords do not match.';
    }

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setIsSuccessOpen(true);
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center px-4 py-4"
      style={{
        background:
          'linear-gradient(135deg, #E8F5E9 0%, #EAF6EB 16.67%, #EBF6ED 33.33%, #EDF7EF 50%, #EEF7F0 66.67%, #F0F8F2 83.33%, #F1F8F4 100%)',
      }}
    >
      <div className="flex flex-col items-center w-full max-w-[672px] gap-[32px]">
        <div className="flex flex-col items-center">
          <div className="w-[63.99px] h-[63.99px] bg-primary rounded-lg flex items-center justify-center shadow-[0px_10px_15px_-3px_rgba(0,0,0,0.1),0px_4px_6px_-4px_rgba(0,0,0,0.1)] mb-[15.99px]">
            <BookOpen className="text-white w-[32px] h-[32px]" />
          </div>
          <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px] tracking-[0.395508px] text-center mb-[3.99px]">
            Create Account
          </h1>
          <p className="text-[16px] font-normal text-[#757575] leading-[24px] tracking-[-0.3125px] text-center">
            Join UniHub to access workshop events
          </p>
        </div>

        <div className="w-full bg-white rounded-[16px] border-[0.555556px] border-[#E0E0E0] shadow-[0px_20px_25px_-5px_rgba(0,0,0,0.1),0px_8px_10px_-6px_rgba(0,0,0,0.1)] px-[32.5521px] pt-[32.5521px] pb-[16.5538px] flex flex-col gap-[23.99px]">
          <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-[23.99px]">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-[23.99px]">
              <div className="flex flex-col gap-[7.99px]">
                <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px]">
                  Full Name *
                </label>
                <input
                  type="text"
                  placeholder="John Doe"
                  value={fullName}
                  onChange={(e) => {
                    setFullName(e.target.value);
                    if (errors.fullName) {
                      setErrors((prev) => ({ ...prev, fullName: undefined }));
                    }
                  }}
                  className={`bg-[#F5F5F5] border-[0.555556px] ${
                    errors.fullName
                      ? 'border-[#C62828] focus:border-[#C62828] focus:ring-[#C62828]'
                      : 'border-[#E0E0E0] focus:border-primary focus:ring-primary'
                  } rounded-[8px] h-[49.1px] px-[16px] py-[12px] text-[16px] leading-[19px] tracking-[-0.3125px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:ring-1 transition-colors`}
                />
                {errors.fullName && (
                  <p className="text-[12px] leading-[16px] text-[#C62828]">{errors.fullName}</p>
                )}
              </div>
              <div className="flex flex-col gap-[7.99px]">
                <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px]">
                  Student ID *
                </label>
                <input
                  type="text"
                  placeholder="STU-2024-001"
                  value={studentId}
                  onChange={(e) => {
                    setStudentId(e.target.value);
                    if (errors.studentId) {
                      setErrors((prev) => ({ ...prev, studentId: undefined }));
                    }
                  }}
                  className={`bg-[#F5F5F5] border-[0.555556px] ${
                    errors.studentId
                      ? 'border-[#C62828] focus:border-[#C62828] focus:ring-[#C62828]'
                      : 'border-[#E0E0E0] focus:border-primary focus:ring-primary'
                  } rounded-[8px] h-[49.1px] px-[16px] py-[12px] text-[16px] leading-[19px] tracking-[-0.3125px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:ring-1 transition-colors`}
                />
                {errors.studentId && (
                  <p className="text-[12px] leading-[16px] text-[#C62828]">{errors.studentId}</p>
                )}
              </div>
              <div className="flex flex-col gap-[7.99px]">
                <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px]">
                  Email Address *
                </label>
                <input
                  type="email"
                  placeholder="john.doe@unihub.edu"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    if (errors.email) {
                      setErrors((prev) => ({ ...prev, email: undefined }));
                    }
                  }}
                  className={`bg-[#F5F5F5] border-[0.555556px] ${
                    errors.email
                      ? 'border-[#C62828] focus:border-[#C62828] focus:ring-[#C62828]'
                      : 'border-[#E0E0E0] focus:border-primary focus:ring-primary'
                  } rounded-[8px] h-[49.1px] px-[16px] py-[12px] text-[16px] leading-[19px] tracking-[-0.3125px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:ring-1 transition-colors`}
                />
                {errors.email && (
                  <p className="text-[12px] leading-[16px] text-[#C62828]">{errors.email}</p>
                )}
              </div>
              <div className="flex flex-col gap-[7.99px]">
                <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px]">
                  Phone Number *
                </label>
                <input
                  type="tel"
                  placeholder="+1 (555) 123-4567"
                  value={phone}
                  onChange={(e) => {
                    setPhone(e.target.value);
                    if (errors.phone) {
                      setErrors((prev) => ({ ...prev, phone: undefined }));
                    }
                  }}
                  className={`bg-[#F5F5F5] border-[0.555556px] ${
                    errors.phone
                      ? 'border-[#C62828] focus:border-[#C62828] focus:ring-[#C62828]'
                      : 'border-[#E0E0E0] focus:border-primary focus:ring-primary'
                  } rounded-[8px] h-[49.1px] px-[16px] py-[12px] text-[16px] leading-[19px] tracking-[-0.3125px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:ring-1 transition-colors`}
                />
                {errors.phone && (
                  <p className="text-[12px] leading-[16px] text-[#C62828]">{errors.phone}</p>
                )}
              </div>
            </div>

            <div className="flex flex-col gap-[7.99px]">
              <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px]">
                Password *
              </label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Create a strong password"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (errors.password) {
                      setErrors((prev) => ({ ...prev, password: undefined }));
                    }
                    if (errors.confirmPassword) {
                      setErrors((prev) => ({ ...prev, confirmPassword: undefined }));
                    }
                  }}
                  className={`w-full bg-[#F5F5F5] border-[0.555556px] ${
                    errors.password
                      ? 'border-[#C62828] focus:border-[#C62828] focus:ring-[#C62828]'
                      : 'border-[#E0E0E0] focus:border-primary focus:ring-primary'
                  } rounded-[8px] h-[49.1px] pl-[16px] pr-[48px] py-[12px] text-[16px] leading-[19px] tracking-[-0.3125px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:ring-1 transition-colors`}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-[16px] top-1/2 -translate-y-1/2 text-[#757575] hover:text-[#1A1A1A] transition-colors"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                </button>
              </div>
              {errors.password && (
                <p className="text-[12px] leading-[16px] text-[#C62828]">{errors.password}</p>
              )}
            </div>

            <div className="flex flex-col gap-[7.99px]">
              <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px] tracking-[-0.150391px]">
                Confirm Password *
              </label>
              <div className="relative">
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  placeholder="Re-enter your password"
                  value={confirmPassword}
                  onChange={(e) => {
                    setConfirmPassword(e.target.value);
                    if (errors.confirmPassword) {
                      setErrors((prev) => ({ ...prev, confirmPassword: undefined }));
                    }
                  }}
                  className={`w-full bg-[#F5F5F5] border-[0.555556px] ${
                    errors.confirmPassword
                      ? 'border-[#C62828] focus:border-[#C62828] focus:ring-[#C62828]'
                      : 'border-[#E0E0E0] focus:border-primary focus:ring-primary'
                  } rounded-[8px] h-[49.1px] pl-[16px] pr-[48px] py-[12px] text-[16px] leading-[19px] tracking-[-0.3125px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:ring-1 transition-colors`}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute right-[16px] top-1/2 -translate-y-1/2 text-[#757575] hover:text-[#1A1A1A] transition-colors"
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                >
                  {showConfirmPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                </button>
              </div>
              {errors.confirmPassword && (
                <p className="text-[12px] leading-[16px] text-[#C62828]">{errors.confirmPassword}</p>
              )}
            </div>

            <div className="w-full bg-[#E3F2FD] border-[0.555556px] border-[#90CAF9] rounded-[8px] px-[16.5538px] pt-[16.5538px] pb-[16.5538px] flex flex-col gap-[7.99px]">
              <div className="text-[14px] font-semibold text-[#0D47A1] leading-[20px] tracking-[-0.150391px]">
                Password Requirements:
              </div>
              <ul className="flex flex-col gap-[3.99px] text-[12px] leading-[16px] text-[#1565C0]">
                <li>Minimum 8 characters long</li>
              </ul>
            </div>

            <button
              type="submit"
              className="w-full h-[47.99px] bg-primary text-white rounded-[8px] text-[16px] font-medium leading-[24px] tracking-[-0.3125px] flex items-center justify-center hover:bg-[#154d19] transition-colors"
            >
              Create Account
            </button>
          </form>

          <div className="border-t-[0.555556px] border-[#E0E0E0] pt-[16.5538px] flex items-center justify-center gap-1">
            <span className="text-[14px] font-normal text-[#757575] leading-[20px] tracking-[-0.150391px]">
              Already have an account?
            </span>
            <Link
              to="/login"
              className="text-[16px] font-medium text-primary hover:underline leading-[24px] tracking-[-0.3125px]"
            >
              Login
            </Link>
          </div>
        </div>
      </div>

      <SuccessModal
        isOpen={isSuccessOpen}
        onClose={() => {
          setIsSuccessOpen(false);
          navigate('/login');
        }}
        title="Account Created!"
        message="Your account has been created successfully. You can now log in."
        buttonText="Go to Login"
      />
    </div>
  );
};
