import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../hooks/useAuthStore';
import { MOCK_STUDENT_USER, MOCK_ORGANIZER_USER, DEMO_CREDENTIALS } from '../../constants/mockData';
import { Eye, EyeOff, BookOpen } from 'lucide-react';
// import { authApi } from '../../api/auth'; // We'll use mock for now as requested

export const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const user = useAuthStore((s) => s.user);

  React.useEffect(() => {
    if (isAuthenticated) {
      navigate(user?.role === 'ORGANIZER' ? '/organizer' : '/');
    }
  }, [isAuthenticated, navigate, user]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      // Simulate API call
      await new Promise((resolve) => setTimeout(resolve, 800));

      // Mock authentication logic
      const isStudent = email === DEMO_CREDENTIALS.student.email && password === DEMO_CREDENTIALS.student.password;
      const isOrganizer = email === DEMO_CREDENTIALS.organizer.email && password === DEMO_CREDENTIALS.organizer.password;
      const isTemp = email === DEMO_CREDENTIALS.temp.email && password === DEMO_CREDENTIALS.temp.password;

      if (isStudent || isTemp) {
        setAuth(MOCK_STUDENT_USER, 'mock-jwt-token-student');
        navigate('/');
      } else if (isOrganizer) {
        setAuth(MOCK_ORGANIZER_USER, 'mock-jwt-token-organizer');
        navigate('/organizer');
      } else {
        setError('Invalid email or password');
      }
    } catch (err) {
      setError('An error occurred during login. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center"
      style={{ background: 'linear-gradient(135deg, #E8F5E9 0%, #F1F8F4 100%)' }}
    >
      <div className="flex flex-col items-center w-full max-w-[448px] gap-8 px-4">
        
        {/* Logo block */}
        <div className="flex flex-col items-center">
          <div className="w-16 h-16 bg-primary rounded-lg flex items-center justify-center shadow-md mb-4">
            <BookOpen className="text-white w-8 h-8" />
          </div>
          <h1 className="text-[30px] font-semibold text-[#1A1A1A] leading-[36px] text-center mb-1">
            Welcome to UniHub
          </h1>
          <p className="text-[16px] font-normal text-[#757575] leading-[24px] text-center">
            Sign in to access your workshop portal
          </p>
        </div>

        {/* Login Card */}
        <div className="w-full bg-white rounded-[16px] border-[1.33px] border-[#E0E0E0] shadow-[0px_20px_25px_-5px_rgba(0,0,0,0.1),0px_8px_10px_-6px_rgba(0,0,0,0.1)] px-[33.33px] pt-[33.33px] pb-[17.33px] flex flex-col gap-[24px]">
          <form onSubmit={handleLogin} className="flex flex-col gap-[24px]">
            
            {/* Email Field */}
            <div className="flex flex-col gap-2">
              <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">
                Email Address
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="student@unihub.edu"
                className="bg-[#F5F5F5] border-[1.33px] border-[#E0E0E0] rounded-lg h-[50.67px] px-4 text-[16px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-colors"
                required
              />
            </div>

            {/* Password Field */}
            <div className="flex flex-col gap-2">
              <label className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">
                Password
              </label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter your password"
                  className="w-full bg-[#F5F5F5] border-[1.33px] border-[#E0E0E0] rounded-lg h-[50.67px] pl-4 pr-12 text-[16px] text-[#1A1A1A] placeholder:text-[rgba(26,26,26,0.5)] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-colors"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-[#757575] hover:text-[#1A1A1A] transition-colors"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                </button>
              </div>
            </div>

            {/* Forgot Password */}
            <div className="flex justify-end">
              <button type="button" className="text-[14px] font-medium text-primary hover:underline">
                Forgot Password?
              </button>
            </div>

            {error && (
              <div className="text-error text-sm text-center bg-[#FFCDD2] bg-opacity-20 py-2 rounded-md">
                {error}
              </div>
            )}

            {/* Login Button */}
            <button
              type="submit"
              disabled={isLoading}
              className="w-full h-[48px] bg-primary text-white rounded-lg text-[16px] font-medium flex items-center justify-center hover:bg-[#154d19] transition-colors disabled:opacity-70 disabled:cursor-not-allowed"
            >
              {isLoading ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin-custom"></div>
              ) : (
                'Login'
              )}
            </button>
          </form>

          {/* Divider & Register */}
          <div className="border-t-[1.33px] border-[#E0E0E0] pt-[17.33px] flex items-center justify-center gap-1">
            <span className="text-[14px] font-normal text-[#757575] leading-[20px]">
              Don't have an account?
            </span>
            <button type="button" className="text-[16px] font-medium text-primary hover:underline leading-[24px]">
              Register
            </button>
          </div>
        </div>

        {/* Demo Credentials */}
        <div className="w-full bg-[#E3F2FD] border-[1.33px] border-[#90CAF9] rounded-lg p-[17.33px] flex flex-col gap-2">
          <div className="text-[12px] font-semibold text-[#0D47A1] leading-[16px]">
            Demo Credentials:
          </div>
          <div className="flex flex-col gap-1">
            <div className="text-[12px] font-normal text-[#1565C0] leading-[16px]">
              Student: student@unihub.edu / student123
            </div>
            <div className="text-[12px] font-normal text-[#1565C0] leading-[16px]">
              Organizer: organizer@unihub.edu / organizer123
            </div>
            <div className="text-[12px] font-normal text-[#1565C0] leading-[16px]">
              Temp Password: temp@unihub.edu / temp123
            </div>
          </div>
        </div>

      </div>
    </div>
  );
};
