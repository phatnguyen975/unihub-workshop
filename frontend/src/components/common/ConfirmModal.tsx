import React from 'react';
import { AlertCircle } from 'lucide-react';

interface ConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmText: string;
  confirmVariant?: 'danger' | 'primary';
}

export const ConfirmModal: React.FC<ConfirmModalProps> = ({ 
  isOpen, 
  onClose, 
  onConfirm, 
  title, 
  message, 
  confirmText,
  confirmVariant = 'danger'
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-[448px] p-8 animate-in fade-in zoom-in duration-300">
        <div className="flex items-center gap-4 mb-6">
          <div className={`w-12 h-12 rounded-full flex items-center justify-center shrink-0 ${
            confirmVariant === 'danger' ? 'bg-red-100' : 'bg-[#E8F5E9]'
          }`}>
            <AlertCircle className={confirmVariant === 'danger' ? 'text-red-600' : 'text-[#1B5E20]'} size={24} />
          </div>
          <h2 className="text-[24px] font-semibold text-[#1A1A1A] leading-[32px]">
            {title}
          </h2>
        </div>
        
        <p className="text-[16px] text-[#757575] leading-[24px] mb-8">
          {message}
        </p>
        
        <div className="flex items-center gap-3">
          <button 
            onClick={onClose}
            className="flex-1 px-6 py-3 rounded-lg border border-[#E0E0E0] text-[#1A1A1A] font-medium text-[16px] hover:bg-gray-50 transition-colors"
          >
            Cancel
          </button>
          <button 
            onClick={() => {
              onConfirm();
              onClose();
            }}
            className={`flex-1 px-6 py-3 rounded-lg text-white font-medium text-[16px] transition-colors ${
              confirmVariant === 'danger' ? 'bg-[#C62828] hover:bg-[#B71C1C]' : 'bg-[#1B5E20] hover:bg-[#144d18]'
            }`}
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
};
