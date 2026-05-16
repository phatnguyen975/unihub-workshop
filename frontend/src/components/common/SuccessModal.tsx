import React from 'react';
import { Check } from 'lucide-react';

interface SuccessModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  message: string;
  buttonText: string;
}

export const SuccessModal: React.FC<SuccessModalProps> = ({ 
  isOpen, 
  onClose, 
  title, 
  message, 
  buttonText 
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-[448px] p-8 flex flex-col items-center text-center animate-in fade-in zoom-in duration-300">
        <div className="w-20 h-20 bg-[#C8E6C9] rounded-full flex items-center justify-center mb-6">
          <Check className="text-[#1B5E20]" size={40} strokeWidth={3} />
        </div>
        
        <h2 className="text-[24px] font-semibold text-[#1A1A1A] leading-[32px] mb-3">
          {title}
        </h2>
        
        <p className="text-[16px] font-semibold text-[#1A1A1A] leading-[24px] mb-10 max-w-[384px]">
          {message}
        </p>
        
        <button 
          onClick={onClose}
          className="w-full bg-[#1B5E20] hover:bg-[#144d18] text-white font-medium text-[16px] py-3 rounded-lg transition-colors"
        >
          {buttonText}
        </button>
      </div>
    </div>
  );
};
